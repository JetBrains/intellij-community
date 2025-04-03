// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.FixingLayoutMatcher;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.*;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.ProcessorWithThrottledCancellationCheck;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.HashMap;
import java.util.function.Function;

import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.*;

public class GotoFileItemProvider extends DefaultChooseByNameItemProvider {
  private static final Logger LOG = Logger.getInstance(GotoFileItemProvider.class);

  public static final int EXACT_MATCH_DEGREE = 5000;
  private static final int DIRECTORY_MATCH_DEGREE = 0;
  private static final int DIR_CHILD_MATCH_DEGREE = 0;

  private final Project myProject;
  private final GotoFileModel myModel;

  public GotoFileItemProvider(@NotNull Project project, @Nullable PsiElement context, @NotNull GotoFileModel model) {
    super(context);
    myProject = project;
    myModel = model;
  }

  @Override
  public boolean filterElementsWithWeights(@NotNull ChooseByNameViewModel base,
                                           @NotNull FindSymbolParameters parameters,
                                           @NotNull ProgressIndicator indicator,
                                           @NotNull Processor<? super FoundItemDescriptor<?>> consumer) {
    return ProgressManager.getInstance().computePrioritized(() -> doFilterElements(base, parameters, indicator, consumer));
  }

  private boolean doFilterElements(@NotNull ChooseByNameViewModel base,
                                   @NotNull FindSymbolParameters parameters,
                                   @NotNull ProgressIndicator indicator,
                                   @NotNull Processor<? super FoundItemDescriptor<?>> consumer) {
    long start = System.currentTimeMillis();
    try {
      String pattern = parameters.getCompletePattern();
      PsiFileSystemItem absolute = getFileByAbsolutePath(pattern);
      if (absolute != null && !consumer.process(new FoundItemDescriptor<>(absolute, EXACT_MATCH_DEGREE))) {
        return true;
      }

      if (pattern.startsWith("./") || pattern.startsWith(".\\")) {
        parameters = parameters.withCompletePattern(pattern.substring(1));
      }

      Ref<Boolean> hasSuggestions = Ref.create(false);
      boolean processItems = processItemsForPattern(base, parameters, consumer, indicator, hasSuggestions);

      // With fuzzy search: The process was interrupt but there are suggestions.
      // For example, if there are too many results,
      // `ContainerUtil.process(matchedFiles, trackingProcessor)` in `SuffixMatcher.processFiles()` returns false
      // and `processItems == false`
      if (!processItems && (!FuzzyFileSearchExperimentOption.isFuzzyFileSearchEnabled() || hasSuggestions.get())) {
        return false;
      }

      Ref<Boolean> hasSuggestionsFixedPattern = Ref.create(false);
      if (processItems) { // stay within the original logic that was before adding the fuzzy search
        String fixedPattern = FixingLayoutMatcher.fixLayout(pattern);
        // With fuzzy search: The process was interrupt but there are suggestions.
        if (fixedPattern != null &&
            !processItemsForPattern(base, parameters.withCompletePattern(fixedPattern), consumer, indicator, hasSuggestionsFixedPattern) &&
            (!FuzzyFileSearchExperimentOption.isFuzzyFileSearchEnabled() || hasSuggestionsFixedPattern.get())) {
          return false;
        }
      }

      return !FuzzyFileSearchExperimentOption.isFuzzyFileSearchEnabled() ||
             hasSuggestions.get() ||
             hasSuggestionsFixedPattern.get() ||
             processItemsForPatternWithLevenshtein(base, parameters, consumer, indicator);
    }
    finally {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Goto File \"" + parameters.getCompletePattern() + "\" took " + (System.currentTimeMillis() - start) + " ms");
      }
    }
  }

  /**
   * Processes all files and directories with `LevenshteinCalculator`.
   * Returns false if the process was stopped, true otherwise.
   */
  private boolean processItemsForPatternWithLevenshtein(final @NotNull ChooseByNameViewModel base,
                                                        @NotNull FindSymbolParameters parameters,
                                                        @NotNull Processor<? super FoundItemDescriptor<?>> consumer,
                                                        @NotNull ProgressIndicator indicator) {
    long start = System.currentTimeMillis();
    final StructuredIdeActivity fuzzySearchActivity =
      FUZZY_SEARCH_ACTIVITY.started(myProject, () -> List.of(FUZZY_SEARCH_TYPE.with(FuzzySearchType.FUZZY_FILE_SEARCH)));

    List<String> patternComponents = LevenshteinCalculator.normalizeString(parameters.getCompletePattern());
    if (patternComponents.isEmpty()) {
      fuzzySearchActivity.finished(() -> List.of(FUZZY_SEARCH_RESULT.with(FuzzySearchResult.EMPTY_PATTERN)));
      return true;
    }

    // Find files that fit the pattern in the original order
    List<FoundItemDescriptor<PsiFileSystemItem>> matchingItems =
      new ArrayList<>(processItemsForDirectPatternWithLevenshtein(base, patternComponents, parameters, indicator));

    // If there are no results, find files that fit the pattern in the inverted order
    if (matchingItems.isEmpty() && patternComponents.size() == 2) {
      List<String> invertedPatternComponents = new ArrayList<>(patternComponents);
      Collections.reverse(invertedPatternComponents);

      matchingItems.addAll(processItemsForDirectPatternWithLevenshtein(base, invertedPatternComponents, parameters, indicator));
    }

    matchingItems.sort((item1, item2) -> Integer.compare(item2.getWeight(), item1.getWeight()));

    Processor<FoundItemDescriptor<?>> trackingProcessor = res -> {
      return consumer.process(res);
    };
    if (!ContainerUtil.process(matchingItems, trackingProcessor)) {
      fuzzySearchActivity.finished(() -> List.of(
                                     FUZZY_SEARCH_TOTAL_RESULTS.with(matchingItems.size()),
                                     FUZZY_SEARCH_RESULT.with(FuzzySearchResult.PROCESS_STOPPED)
                                   )
      );
      return false;
    }

    fuzzySearchActivity.finished(() -> List.of(
                                   FUZZY_SEARCH_TOTAL_RESULTS.with(matchingItems.size()),
                                   FUZZY_SEARCH_RESULT.with(FuzzySearchResult.PROCESS_COMPLETE)
                                 )
    );

    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "Process items with levenshtein \"" + parameters.getCompletePattern() + "\" took " + (System.currentTimeMillis() - start) + " ms");
    }

    return true;
  }

  private List<FoundItemDescriptor<PsiFileSystemItem>> processItemsForDirectPatternWithLevenshtein(final @NotNull ChooseByNameViewModel base,
                                                                                                   @NotNull List<String> patternComponents,
                                                                                                   @NotNull FindSymbolParameters parameters,
                                                                                                   @NotNull ProgressIndicator indicator) {
    if (patternComponents.isEmpty()) {
      return Collections.emptyList();
    }

    GlobalSearchScope searchScope = parameters.getSearchScope();

    // Find all directories and files names similar to the last component in patternComponents
    List<MatchResult> matchingNames = new ArrayList<>();
    String lastPatternComponent = patternComponents.get(patternComponents.size() - 1);
    MinusculeMatcher matcher = buildPatternMatcher(lastPatternComponent, true);
    var nameMatchingCheck = new ProcessorWithThrottledCancellationCheck<>(
      (CharSequence fileNameCharSeq) -> {
        indicator.checkCanceled();
        if (fileNameCharSeq != null) {
          String fileName = fileNameCharSeq.toString();
          MatchResult result = matches(base, parameters.getCompletePattern(), matcher, fileName);
          if (result != null) {
            matchingNames.add(result);
          }
          else {
            String nameWithoutExtension = FileUtil.getNameWithoutExtension(fileName); // FIXME: directories can contain a dot
            float distance = LevenshteinCalculator.distanceBetweenStrings(nameWithoutExtension, lastPatternComponent);
            if (distance > LevenshteinCalculator.MIN_ACCEPTABLE_DISTANCE) {
              matchingNames.add(new MatchResult(fileName, LevenshteinCalculator.weightFromDistance(distance), false));
            }
          }
        }
        return true;
      }
    );
    FilenameIndex.processAllFileNames(nameMatchingCheck, searchScope, parameters.getIdFilter());

    if (matchingNames.isEmpty()) {
      return Collections.emptyList();
    }

    Function<String, Object[]> indexResult = key -> myModel.getElementsByName(key, parameters.withScope(searchScope), indicator);
    JBIterable<FoundItemDescriptor<PsiFileSystemItem>> filesMatchingPath = JBIterable.from(matchingNames)
      .flatMap(nameGroup -> getItemsForNames(searchScope, Collections.singletonList(nameGroup), indexResult));

    List<FoundItemDescriptor<PsiFileSystemItem>> matchingItems = new ArrayList<>();
    List<PsiDirectory> matchingDirectories = new ArrayList<>();
    for (FoundItemDescriptor<PsiFileSystemItem> itemDescriptor : filesMatchingPath) {
      PsiFileSystemItem psiFileItem = itemDescriptor.getItem();
      int psiFileItemWeight = itemDescriptor.getWeight();

      if (patternComponents.size() > 1) {
        int patternSize = patternComponents.size();
        LevenshteinCalculator calculator = new LevenshteinCalculator(patternComponents.subList(0, patternSize - 1));
        float distance = calculator.distanceToVirtualFile(psiFileItem.getVirtualFile().getParent(), false, false);
        if (distance >= LevenshteinCalculator.MIN_ACCEPTABLE_DISTANCE) {
          int avgWeight = (psiFileItemWeight + LevenshteinCalculator.weightFromDistance(distance) * (patternSize - 1)) / patternSize;
          matchingItems.add(new FoundItemDescriptor<>(psiFileItem, avgWeight));
        }
      }
      else {
        matchingItems.add(itemDescriptor);
        if (psiFileItem instanceof PsiDirectory directory) {
          matchingDirectories.add(directory);
        }
      }
    }

    if (matchingDirectories.size() == 1) {
      List<FoundItemDescriptor<PsiFileSystemItem>> childElements = getListWithChildItems(matchingDirectories.get(0), myProject);
      matchingItems.addAll(childElements);
    }

    return matchingItems;
  }

  private static class DirectoryConsumer {
    private @Nullable PsiDirectory directory;
    private boolean moreThanOne = false;
    private final boolean isConsumeEnabled;

    DirectoryConsumer(boolean isConsumeEnabled) {
      this.isConsumeEnabled = isConsumeEnabled;
    }

    public void consume(PsiDirectory anotherDirectory) {
      if (!isConsumeEnabled) {
        return;
      }
      if (directory == null) {
        directory = anotherDirectory;
      }
      else {
        moreThanOne = true;
      }
    }

    public @Nullable PsiDirectory getDirectoryIfTheOnlyOne() {
      if (!moreThanOne) {
        return directory;
      }
      return null;
    }
  }

  private boolean processItemsForPattern(@NotNull ChooseByNameViewModel base,
                                         @NotNull FindSymbolParameters parameters,
                                         @NotNull Processor<? super FoundItemDescriptor<?>> consumer,
                                         @NotNull ProgressIndicator indicator,
                                         @NotNull Ref<Boolean> hasSuggestions) {
    String sanitized = getSanitizedPattern(parameters.getCompletePattern(), myModel);
    int qualifierEnd = sanitized.lastIndexOf('/') + 1;
    NameGrouper grouper = new NameGrouper(sanitized.substring(qualifierEnd), indicator);
    processNames(parameters, name -> grouper.processName(name));

    DirectoryPathMatcher dirMatcher = DirectoryPathMatcher.root(myModel, sanitized.substring(0, qualifierEnd));
    DirectoryConsumer directoryConsumer = new DirectoryConsumer(FuzzyFileSearchExperimentOption.isFuzzyFileSearchEnabled());
    while (dirMatcher != null) {
      int index = grouper.index;
      SuffixMatches group = grouper.nextGroup(base);
      if (group == null) break;
      if (!group.processFiles(parameters.withLocalPattern(dirMatcher.dirPattern), consumer, hasSuggestions, dirMatcher,
                              directoryConsumer)) {
        return false;
      }
      dirMatcher = dirMatcher.appendChar(grouper.namePattern.charAt(index));
      if (!myModel.isSlashlessMatchingEnabled()) {
        return true;
      }
    }

    PsiDirectory directory = directoryConsumer.getDirectoryIfTheOnlyOne();
    if (directory != null) {
      List<FoundItemDescriptor<PsiFileSystemItem>> childElements = getListWithChildItems(directory, myProject);
      if (!ContainerUtil.process(childElements, consumer)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Invoke contributors directly, as multi-threading isn't of much value in Goto File,
   * and filling {@link ContributorsBasedGotoByModel#myContributorToItsSymbolsMap} is expensive for the default contributor.
   */
  private void processNames(@NotNull FindSymbolParameters parameters, @NotNull Processor<? super String> nameProcessor) {
    List<ChooseByNameContributor> contributors = DumbService.getDumbAwareExtensions(myProject, ChooseByNameContributor.FILE_EP_NAME);
    for (ChooseByNameContributor contributor : contributors) {
      if (contributor instanceof DefaultFileNavigationContributor) {
        FilenameIndex.processAllFileNames(nameProcessor, parameters.getSearchScope(), parameters.getIdFilter());
      }
      else {
        myModel.processContributorNames(contributor, parameters, nameProcessor);
      }
    }
  }

  public static @NotNull String getSanitizedPattern(@NotNull String pattern, @NotNull GotoFileModel model) {
    return removeSlashes(StringUtil.replace(ChooseByNamePopup.getTransformedPattern(pattern, model), "\\", "/"));
  }

  public static @NotNull MinusculeMatcher getQualifiedNameMatcher(@NotNull String pattern) {
    pattern = "*" + StringUtil.replace(StringUtil.replace(pattern, "\\", "*\\*"), "/", "*/*");

    return NameUtil.buildMatcher(pattern)
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .preferringStartMatches()
      .build();
  }

  private static @NotNull String removeSlashes(@NotNull String s) {
    return UriUtil.trimLeadingSlashes(UriUtil.trimTrailingSlashes(s));
  }

  private @Nullable PsiFileSystemItem getFileByAbsolutePath(@NotNull String pattern) {
    if (pattern.contains("/") || pattern.contains("\\")) {
      String path = FileUtil.toSystemIndependentName(ChooseByNamePopup.getTransformedPattern(pattern, myModel));
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPathIfCached(path);
      if (vFile == null) {
        path = unitePaths(myProject.getBasePath(), path);
        if (path != null) vFile = LocalFileSystem.getInstance().findFileByPathIfCached(path);
      }
      if (vFile != null) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
        if (index.isInContent(vFile) || index.isInLibrary(vFile)) {
          return PsiUtilCore.findFileSystemItem(myProject, vFile);
        }
      }
    }
    return null;
  }

  public static String unitePaths(String projectPathStr, String filePathStr) {
    if (filePathStr.startsWith("/")) return filePathStr;

    List<String> path = new ArrayList<>(StringUtil.split(projectPathStr, "/"));
    StringBuilder prefix = new StringBuilder();

    while (!filePathStr.startsWith(StringUtil.join(path, "/"))) {
      prefix.append(path.remove(0)).append("/");
      if (path.isEmpty()) return null;
    }

    return prefix.append(filePathStr).toString();
  }

  private @NotNull Iterable<FoundItemDescriptor<PsiFileSystemItem>> matchQualifiers(@NotNull MinusculeMatcher qualifierMatcher,
                                                                                    JBIterable<? extends FoundItemDescriptor<PsiFileSystemItem>> iterable,
                                                                                    @NotNull String completePattern) {
    List<FoundItemDescriptor<PsiFileSystemItem>> matching = new ArrayList<>();
    for (FoundItemDescriptor<PsiFileSystemItem> descriptor : iterable) {
      PsiFileSystemItem item = descriptor.getItem();
      ProgressManager.checkCanceled();

      String qualifier = Objects.requireNonNull(getParentPath(item));
      FList<TextRange> fragments = qualifierMatcher.matchingFragments(qualifier);
      if (fragments != null) {
        int gapPenalty = fragments.isEmpty() ? 0 : qualifier.length() - fragments.get(fragments.size() - 1).getEndOffset();
        int exactMatchScore = isExactMatch(item, completePattern) ? EXACT_MATCH_DEGREE : 0;
        int qualifierDegree = qualifierMatcher.matchingDegree(qualifier, false, fragments) - gapPenalty + exactMatchScore;
        matching.add(new FoundItemDescriptor<>(item, qualifierDegree));
      }
      else if (isExactMatch(item, completePattern)) {
        matching.add(new FoundItemDescriptor<>(item, EXACT_MATCH_DEGREE));
      }
    }
    if (matching.size() > 1) {
      Comparator<FoundItemDescriptor<PsiFileSystemItem>> comparator =
        Comparator.comparing((FoundItemDescriptor<PsiFileSystemItem> res) -> res.getWeight()).reversed();
      matching.sort(comparator);
    }
    return matching;
  }

  private boolean isExactMatch(@NotNull PsiFileSystemItem item, @NotNull String completePattern) {
    String fullName = myModel.getFullName(item);
    return fullName != null && isSubpath(fullName, completePattern);
  }

  private boolean isSubpath(@NotNull String path, String subpath) {
    subpath = ChooseByNamePopup.getTransformedPattern(subpath, myModel).stripTrailing();
    path = FileUtilRt.toSystemIndependentName(path);
    subpath = FileUtilRt.toSystemIndependentName(subpath);
    return path.endsWith(subpath);
  }

  private @Nullable String getParentPath(@NotNull PsiFileSystemItem item) {
    String fullName = myModel.getFullName(item);
    return fullName == null ? null : StringUtil.getPackageName(FileUtilRt.toSystemIndependentName(fullName), '/') + '/';
  }

  private static @NotNull JBIterable<FoundItemDescriptor<PsiFileSystemItem>> moveDirectoriesToEnd(@NotNull Iterable<? extends FoundItemDescriptor<PsiFileSystemItem>> iterable,
                                                                                                  @NotNull DirectoryConsumer directoryConsumer) {
    List<FoundItemDescriptor<PsiFileSystemItem>> dirs = new ArrayList<>();
    return JBIterable.<FoundItemDescriptor<PsiFileSystemItem>>from(iterable).filter(res -> {
      if (res.getItem() instanceof PsiDirectory psiDirectory) {
        dirs.add(new FoundItemDescriptor<>(psiDirectory, DIRECTORY_MATCH_DEGREE));
        directoryConsumer.consume(psiDirectory);
        return false;
      }
      return true;
    }).append(dirs);
  }

  private static @NotNull List<FoundItemDescriptor<PsiFileSystemItem>> getListWithChildItems(@NotNull PsiDirectory directory,
                                                                                             @NotNull Project project) {
    List<FoundItemDescriptor<PsiFileSystemItem>> childElements = new ArrayList<>();
    for (VirtualFile childElement : directory.getVirtualFile().getChildren()) {
      if (!childElement.isDirectory()) {
        PsiFileSystemItem fileItem = PsiUtilCore.findFileSystemItem(project, childElement);
        if (fileItem != null) {
          childElements.add(new FoundItemDescriptor<>(fileItem, DIR_CHILD_MATCH_DEGREE));
        }
      }
    }
    return childElements;
  }


  private @Unmodifiable @NotNull Iterable<FoundItemDescriptor<PsiFileSystemItem>> getItemsForNames(@NotNull GlobalSearchScope scope,
                                                                                                   @NotNull List<? extends MatchResult> matchResults,
                                                                                                   @NotNull Function<? super String, Object[]> indexResult) {
    List<PsiFileSystemItem> group = new ArrayList<>();
    Map<PsiFileSystemItem, Integer> nesting = new HashMap<>();
    Map<PsiFileSystemItem, Integer> matchDegrees = new HashMap<>();
    for (MatchResult matchResult : matchResults) {
      Object[] items = indexResult.apply(matchResult.elementName);
      ProgressManager.checkCanceled();
      for (Object item : items) {
        if (!(item instanceof PsiFileSystemItem psiItem)) continue;
        if (!scope.contains(psiItem.getVirtualFile())) continue;
        String qualifier = getParentPath(psiItem);
        if (qualifier != null) {
          group.add(psiItem);
          nesting.put(psiItem, StringUtil.countChars(qualifier, '/'));
          matchDegrees.put(psiItem, matchResult.matchingDegree);
        }
      }
    }

    if (group.size() > 1) {
      group.sort(getPathProximityComparator().
        thenComparing(nesting::get).
        thenComparing(myModel::getFullName));
    }
    return ContainerUtil.map(group, item -> new FoundItemDescriptor<>(item, matchDegrees.get(item)));
  }

  /**
   * @return Minimal {@code pos} such that {@code candidateName} can potentially match {@code namePattern.substring(pos)}
   * (i.e. contains all the letters from  {@code namePattern.substring(pos)} sub-sequence, in that order).
   * Matching attempts with longer pattern substrings certainly will fail.
   */
  private static int findMatchStartingPosition(@NotNull String candidateName, char @NotNull [] name_pattern, char @NotNull [] NAME_PATTERN) {
    int candidatePos = candidateName.length();
    int pos;
    for (pos = name_pattern.length; pos > 0; pos--) {
      char c = name_pattern[pos - 1];
      if (!Character.isLetterOrDigit(c)) continue;
      char C = NAME_PATTERN[pos - 1];
      for (candidatePos--; candidatePos >= 0; candidatePos--) {
        char candidateC = candidateName.charAt(candidatePos);
        if (candidateC == c || candidateC == C) {
          break;
        }
      }
      if (candidatePos < 0) {
        break;
      }
    }
    return pos;
  }

  private final class NameGrouper {
    private final String namePattern;
    private final char[] NAME_PATTERN; // upper cased namePattern
    private final char[] name_pattern; // lower cased namePattern
    private final @NotNull ProgressIndicator indicator;

    /** Names placed into buckets where the index of bucket == {@link #findMatchStartingPosition} */
    private final List<List<String>> candidateNames;

    private int index;

    NameGrouper(@NotNull String namePattern, @NotNull ProgressIndicator indicator) {
      this.namePattern = namePattern;
      name_pattern = new char[namePattern.length()];
      NAME_PATTERN = new char[namePattern.length()];
      candidateNames = new ArrayList<>(namePattern.length());
      for (int i = 0; i < namePattern.length(); i++) {
        candidateNames.add(new ArrayList<>());
        char c = namePattern.charAt(i);
        name_pattern[i] = Character.toLowerCase(c);
        NAME_PATTERN[i] = Character.toUpperCase(c);
      }
      this.indicator = indicator;
    }

    boolean processName(@NotNull String name) {
      indicator.checkCanceled();
      int position = findMatchStartingPosition(name, name_pattern, NAME_PATTERN);
      if (position < namePattern.length()) {
        candidateNames.get(position).add(name);
      }
      return true;
    }

    @Nullable
    SuffixMatches nextGroup(@NotNull ChooseByNameViewModel base) {
      if (index >= namePattern.length()) return null;

      SuffixMatches matches = new SuffixMatches(namePattern, index, indicator);
      for (String name : candidateNames.get(index)) {
        if (!matches.matchName(base, name) && index + 1 < namePattern.length()) {
          candidateNames.get(index + 1).add(name); // try later with a shorter matcher
        }
      }
      candidateNames.set(index, null);
      index++;
      return matches;
    }
  }

  private final class SuffixMatches {
    final String patternSuffix;
    final MinusculeMatcher matcher;
    final List<MatchResult> matchingNames = new ArrayList<>();
    final ProgressIndicator indicator;

    SuffixMatches(@NotNull String pattern, int from, @NotNull ProgressIndicator indicator) {
      patternSuffix = pattern.substring(from);
      boolean preferStartMatches = from == 0 && !patternSuffix.startsWith("*");
      String matchPattern = (from > 0 ? " " : "*") + patternSuffix;

      NameUtil.MatcherBuilder builder = NameUtil.buildMatcher(matchPattern).withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE);
      if (preferStartMatches) {
        builder.preferringStartMatches();
      }

      this.matcher = builder.build();
      this.indicator = indicator;
    }

    @Override
    public @NonNls String toString() {
      return "SuffixMatches{" +
             "patternSuffix='" + patternSuffix + '\'' +
             ", matchingNames=" + matchingNames +
             '}';
    }

    boolean matchName(@NotNull ChooseByNameViewModel base, String name) {
      MatchResult result = matches(base, patternSuffix, matcher, name);
      if (result != null) {
        matchingNames.add(result);
        return true;
      }
      return false;
    }

    boolean processFiles(@NotNull FindSymbolParameters parameters,
                         @NotNull Processor<? super FoundItemDescriptor<?>> processor,
                         @NotNull Ref<Boolean> hasSuggestions,
                         @NotNull DirectoryPathMatcher dirMatcher,
                         @NotNull DirectoryConsumer directoryConsumer) {
      List<MatchResult> matchingNames = this.matchingNames;
      if (patternSuffix.length() <= 3 && !dirMatcher.dirPattern.isEmpty()) {
        // just enumerate over files
        // otherwise there are too many names matching the remaining few letters, and querying index for all of them with a very constrained scope is expensive
        Set<String> existingNames = dirMatcher.findFileNamesMatchingIfCheap(patternSuffix.charAt(0), matcher);
        if (existingNames != null) {
          matchingNames = ContainerUtil.filter(matchingNames, mr -> existingNames.contains(mr.elementName));
        }
      }
      MinusculeMatcher qualifierMatcher = getQualifiedNameMatcher(parameters.getLocalPatternName());
      Comparator<MatchResult> byNameWithoutExtension = Comparator.comparing(
        mr -> StringUtil.toLowerCase(FileUtilRt.getNameWithoutExtension(mr.elementName)));
      Comparator<MatchResult> matchingDegreeComparator = matchingDegreeComparator();
      matchingNames = ContainerUtil.sorted(matchingNames, matchingDegreeComparator);
      // comparator1.thenComparing(comparator2) is too slow, let's lazily apply comparator2 as needed below
      Function<List<MatchResult>, List<MatchResult>> sortGroup = new Function<>() {
        final Set<Object> sortedGroups = CollectionFactory.createCustomHashingStrategySet(HashingStrategy.identity());
        @Override
        public List<MatchResult> apply(List<MatchResult> results) {
          if (sortedGroups.add(results)) {
            results.sort(byNameWithoutExtension);
          }
          return results;
        }
      };

      GlobalSearchScope scope = dirMatcher.narrowDown(parameters.getSearchScope());
      FindSymbolParameters parametersAdjusted = parameters.withScope(scope);

      List<List<MatchResult>> groups = group(matchingNames, matchingDegreeComparator);
      Function<String, Object[]> indexResult = key -> myModel.getElementsByName(key, parametersAdjusted, indicator);

      for (List<MatchResult> group : groups) {
        List<List<MatchResult>> sortedNames = group(sortGroup.apply(group), byNameWithoutExtension);
        JBIterable<FoundItemDescriptor<PsiFileSystemItem>> filesMatchingPath = JBIterable.from(sortedNames)
          .flatMap(nameGroup -> getItemsForNames(scope, nameGroup, indexResult));
        Iterable<FoundItemDescriptor<PsiFileSystemItem>> matchedFiles =
          parameters.getLocalPatternName().isEmpty()
          ? filesMatchingPath
          : matchQualifiers(qualifierMatcher, filesMatchingPath, parameters.getCompletePattern());

        matchedFiles = moveDirectoriesToEnd(matchedFiles, directoryConsumer);

        Processor<FoundItemDescriptor<PsiFileSystemItem>> trackingProcessor = res -> {
          hasSuggestions.set(true);
          return processor.process(res);
        };
        if (!ContainerUtil.process(matchedFiles, trackingProcessor)) {
          return false;
        }
      }

      // let the framework switch to searching outside project to display these well-matching suggestions
      // instead of worse-matching ones in project (that are very expensive to calculate)
      return hasSuggestions.get() ||
             parameters.isSearchInLibraries() ||
             !hasSuggestionsOutsideProject(parameters.getCompletePattern(), matchingNames, dirMatcher);
    }

    private boolean hasSuggestionsOutsideProject(@NotNull String pattern,
                                                 @NotNull List<? extends MatchResult> group,
                                                 @NotNull DirectoryPathMatcher dirMatcher) {
      FindSymbolParameters parameters = FindSymbolParameters.wrap(pattern, myProject, true);
      GlobalSearchScope scope = dirMatcher.narrowDown(parameters.getSearchScope());
      FindSymbolParameters adjusted = parameters.withScope(scope);
      for (MatchResult matchResult : group) {
        for (Object o : myModel.getElementsByName(matchResult.elementName, adjusted, indicator)) {
          ProgressManager.checkCanceled();
          if (o instanceof PsiFileSystemItem psiItem) {
            String qualifier = getParentPath(psiItem);
            if (qualifier != null) return true;
          }
        }
      }
      return false;
    }

    private @NotNull Comparator<MatchResult> matchingDegreeComparator() {
      return (mr1, mr2) -> {
        boolean exactPrefix1 = StringUtil.startsWith(mr1.elementName, patternSuffix);
        boolean exactPrefix2 = StringUtil.startsWith(mr2.elementName, patternSuffix);
        if (exactPrefix1 && exactPrefix2) return 0;
        if (exactPrefix1 != exactPrefix2) return exactPrefix1 ? -1 : 1;
        return mr1.compareDegrees(mr2);
      };
    }
  }

  private static @NotNull <T> List<List<T>> group(@NotNull List<T> items, @NotNull Comparator<? super T> comparator) {
    return StreamEx.of(items).groupRuns((n1, n2) -> comparator.compare(n1, n2) == 0).toList();
  }
}