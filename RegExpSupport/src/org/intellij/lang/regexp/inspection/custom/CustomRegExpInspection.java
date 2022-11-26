// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CustomRegExpInspection extends LocalInspectionTool implements DynamicGroupTool {

  public static final String SHORT_NAME = "CustomRegExpInspection";
  public final List<RegExpInspectionConfiguration> myConfigurations = new SmartList<>();
  private InspectionProfileImpl mySessionProfile;

  public CustomRegExpInspection() {
    /*
    final FileType javaFileType = FileTypeManager.getInstance().getStdFileType("JAVA");
    final RegExpInspectionConfiguration one = new RegExpInspectionConfiguration("No spaces within parentheses");
    one.patterns.add(new RegExpInspectionConfiguration.InspectionPattern("(\\()\\s+|\\s+(\\))", null, FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS, "$1"));
    one.suppressId = "NoSpaces";
    one.description = "We don't like spaces within parentheses in our code style";
    myConfigurations.add(one);

    final RegExpInspectionConfiguration two = new RegExpInspectionConfiguration("No more than one empty line in Java");
    two.patterns.add(new RegExpInspectionConfiguration.InspectionPattern("\\n\\n\\n+", javaFileType, FindModel.SearchContext.EXCEPT_STRING_LITERALS, "\n\n"));
    two.suppressId = "EmptyLines";
    two.description = "One empty line should be enough for everybody";
    myConfigurations.add(two);

    final RegExpInspectionConfiguration three = new RegExpInspectionConfiguration("Trailing whitespace");
    three.patterns.add(new RegExpInspectionConfiguration.InspectionPattern(" +\\n", PlainTextFileType.INSTANCE, FindModel.SearchContext.ANY, ""));
    three.patterns.add(new RegExpInspectionConfiguration.InspectionPattern("\t+\\n", PlainTextFileType.INSTANCE, FindModel.SearchContext.ANY, ""));
    three.description = "Trailing whitespace is unnecessary";
    myConfigurations.add(three);

    final RegExpInspectionConfiguration four = new RegExpInspectionConfiguration("Multiple spaces in Java");
    four.patterns.add(new RegExpInspectionConfiguration.InspectionPattern("(?<=\\S) {2,}", javaFileType, FindModel.SearchContext.EXCEPT_COMMENTS, " "));
    four.description = "Double spaced";
    myConfigurations.add(four);
    */
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContext context) {
    super.initialize(context);
    mySessionProfile = ((GlobalInspectionContextBase)context).getCurrentProfile();
  }

  @Override
  public void cleanup(@NotNull Project project) {
    super.cleanup(project);
    mySessionProfile = null;
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final Document document = file.getViewProvider().getDocument();
    final CharSequence text = document.getCharsSequence();
    final FindManager findManager = FindManager.getInstance(file.getProject());
    final Project project = manager.getProject();
    final InspectionProfileImpl profile =
      (mySessionProfile != null && !isOnTheFly) ? mySessionProfile : InspectionProfileManager.getInstance(project).getCurrentProfile();
    final List<ProblemDescriptor> descriptors = new SmartList<>();
    for (RegExpInspectionConfiguration configuration : myConfigurations) {
      final String uuid = configuration.getUuid();
      final ToolsImpl tools = profile.getToolsOrNull(uuid, project);
      if (tools != null && !tools.isEnabled(file)) {
        continue;
      }
      addInspectionToProfile(project, profile, configuration); // hack
      register(configuration);

      for (RegExpInspectionConfiguration.InspectionPattern pattern : configuration.getPatterns()) {
        if (file.getFileType() != pattern.fileType()) continue;
        final FindModel model = new FindModel();
        model.setRegularExpressions(true);
        model.setStringToFind(pattern.regExp());
        final String replacement = pattern.replacement();
        if (replacement != null) {
          model.setStringToReplace(replacement);
        }
        model.setSearchContext(pattern.searchContext());
        FindResult result = findManager.findString(text, 0, model);
        while (result.isStringFound()) {
          final TextRange range = new TextRange(result.getStartOffset(), result.getEndOffset());
          PsiElement element = file.findElementAt(result.getStartOffset());
          assert element != null;
          while (!element.getTextRange().contains(range)) {
            element = element.getParent();
          }
          final TextRange elementRange = element.getTextRange();
          final int start = result.getStartOffset() - elementRange.getStartOffset();
          final TextRange warningRange = new TextRange(start, result.getEndOffset() - result.getStartOffset() + start);
          final String problemDescriptor = StringUtil.defaultIfEmpty(configuration.getProblemDescriptor(), configuration.getName());
          final ProblemDescriptor descriptor =
            manager.createProblemDescriptor(element, warningRange, problemDescriptor,
                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
                                            new CustomRegExpQuickFix(findManager, model, text, result));
          descriptors.add(new ProblemDescriptorWithReporterName((ProblemDescriptorBase)descriptor, uuid));
          result = findManager.findString(text, result.getEndOffset(), model);
        }
      }
    }

    return descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  public static void register(@NotNull RegExpInspectionConfiguration configuration) {
    // modify from single (event) thread, to prevent race conditions.
    ApplicationManager.getApplication().invokeLater(() -> {
      final String shortName = configuration.getUuid();
      final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (key != null) {
        if (!isMetaDataChanged(configuration, key)) return;
        HighlightDisplayKey.unregister(shortName);
      }
      final String suppressId = configuration.getSuppressId();
      final String name = configuration.getName();
      if (suppressId == null) {
        HighlightDisplayKey.register(shortName, () -> name, SHORT_NAME);
      }
      else {
        HighlightDisplayKey.register(shortName, () -> name, suppressId, SHORT_NAME);
      }
    }, ModalityState.NON_MODAL);
  }

  private static boolean isMetaDataChanged(@NotNull RegExpInspectionConfiguration configuration, @NotNull HighlightDisplayKey key) {
    if (StringUtil.isEmpty(configuration.getSuppressId())) {
      if (!SHORT_NAME.equals(key.getID())) return true;
    }
    else if (!configuration.getSuppressId().equals(key.getID())) return true;
    return !configuration.getName().equals(HighlightDisplayKey.getDisplayNameByKey(key));
  }

  public static void addInspectionToProfile(@NotNull Project project,
                                            @NotNull InspectionProfileImpl profile,
                                            @NotNull RegExpInspectionConfiguration configuration) {
    final String shortName = configuration.getUuid();
    final InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(shortName, project);
    if (toolWrapper != null) {
      // already added
      return;
    }
    final CustomRegExpInspectionToolWrapper wrapped = new CustomRegExpInspectionToolWrapper(configuration);
    profile.addTool(project, wrapped, null);
    profile.setToolEnabled(shortName, true);
  }

  @Override
  public @NotNull List<LocalInspectionToolWrapper> getChildren() {
    return ContainerUtil.map(myConfigurations, CustomRegExpInspectionToolWrapper::new);
  }

  public void addConfiguration(RegExpInspectionConfiguration configuration) {
    myConfigurations.add(configuration);
  }

  public void updateConfiguration(RegExpInspectionConfiguration configuration) {
    myConfigurations.remove(configuration);
    myConfigurations.add(configuration);
  }

  public void removeConfigurationWithUuid(String uuid) {
    myConfigurations.removeIf(c -> c.getUuid().equals(uuid));
  }

  public List<RegExpInspectionConfiguration> getConfigurations() {
    return myConfigurations;
  }

  private static class CustomRegExpQuickFix implements LocalQuickFix {
    private final int myStartOffset;
    private final int myEndOffset;
    private final String myReplacement;
    private final String myOriginal;

    private CustomRegExpQuickFix(FindManager findManager, FindModel findModel, CharSequence text, FindResult result) {
      myStartOffset = result.getStartOffset();
      myEndOffset = result.getEndOffset();
      myOriginal = text.subSequence(myStartOffset, myEndOffset).toString();
      try {
        myReplacement = findManager.getStringToReplace(myOriginal, findModel, myStartOffset, text);
      }
      catch (FindManager.MalformedReplacementStringException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacement);
    }

    @Override
    public @NotNull String getFamilyName() {
      return RegExpBundle.message("intention.family.name.replace");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile file = descriptor.getPsiElement().getContainingFile();
      final Document document = file.getViewProvider().getDocument();
      if (myOriginal.equals(document.getText(TextRange.create(myStartOffset, myEndOffset)))) {
        document.replaceString(myStartOffset, myEndOffset, myReplacement);
      }
    }
  }
}
