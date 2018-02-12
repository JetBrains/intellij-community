/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Completion FAQ:<p>
 *
 * Q: How do I implement code completion?<br>
 * A: Define a completion.contributor extension of type {@link CompletionContributor}.
 * Or, if the place you want to complete in contains a {@link PsiReference}, just return the variants
 * you want to suggest from its {@link PsiReference#getVariants()} method as {@link String}s,
 * {@link PsiElement}s, or better {@link LookupElement}s.<p>
 *
 * Q: OK, but what to do with CompletionContributor?<br>
 * A: There are two ways. The easier and preferred one is to provide constructor in your contributor and register completion providers there:
 * {@link #extend(CompletionType, ElementPattern, CompletionProvider)}.<br>
 * A more generic way is to override default {@link #fillCompletionVariants(CompletionParameters, CompletionResultSet)} implementation
 * and provide your own. It's easier to debug, but harder to write.<p>
 *
 * Q: What does the {@link CompletionParameters#getPosition()} return?<br>
 * A: When completion is invoked, the file being edited is first copied (the original file can be accessed from {@link com.intellij.psi.PsiFile#getOriginalFile()}
 * and {@link CompletionParameters#getOriginalFile()}. Then a special 'dummy identifier' string is inserted to the copied file at caret offset (removing the selection).
 * Most often this string is an identifier (see {@link CompletionInitializationContext#DUMMY_IDENTIFIER}).
 * This is usually done to guarantee that there'll always be some non-empty element there, which will be easy to describe via {@link ElementPattern}s.
 * Also a reference can suddenly appear in that position, which will certainly help invoking its {@link PsiReference#getVariants()}.
 * Dummy identifier string can be easily changed in {@link #beforeCompletion(CompletionInitializationContext)} method.<p>
 *
 * Q: How do I get automatic lookup element filtering by prefix?<br>
 * A: When you return variants from reference ({@link PsiReference#getVariants()}), the filtering will be done
 * automatically, with prefix taken as the reference text from its start ({@link PsiReference#getRangeInElement()}) to
 * the caret position.
 * In {@link CompletionContributor} you will be given a {@link CompletionResultSet}
 * which will match {@link LookupElement}s against its prefix matcher {@link CompletionResultSet#getPrefixMatcher()}.
 * If the default prefix calculated by the IDE doesn't satisfy you, you can obtain another result set via
 * {@link CompletionResultSet#withPrefixMatcher(PrefixMatcher)} and feed your lookup elements to the latter.
 * It's one of the item's lookup strings ({@link LookupElement#getAllLookupStrings()} that is matched against prefix matcher.<p>
 *
 * Q: How do I plug into those funny texts below the items in shown lookup?<br>
 * A: Use {@link CompletionResultSet#addLookupAdvertisement(String)} <p>
 *
 * Q: How do I change the text that gets shown when there are no suitable variants at all? <br>
 * A: Use {@link CompletionContributor#handleEmptyLookup(CompletionParameters, Editor)}.
 * Don't forget to check whether you are in correct place (see {@link CompletionParameters}).<p>
 *
 * Q: How do I affect lookup element's appearance (icon, text attributes, etc.)?<br>
 * A: See {@link LookupElement#renderElement(LookupElementPresentation)}.<p>
 *
 * Q: I'm not satisfied that completion just inserts the item's lookup string on item selection. How to make it write something else?<br>
 * A: See {@link LookupElement#handleInsert(InsertionContext)}.<p>
 *
 * Q: What if I select item with TAB key?<br>
 * A: Semantics is, that the identifier that you're standing inside gets removed completely, and then the lookup string is inserted. You can change
 * the deleting range end offset, do it in {@link CompletionContributor#beforeCompletion(CompletionInitializationContext)}
 * by putting new offset to {@link CompletionInitializationContext#getOffsetMap()} as {@link CompletionInitializationContext#IDENTIFIER_END_OFFSET}.<p>
 *
 * Q: I know more about my environment than the IDE does, and I can swear that those 239 variants it suggests me in some place aren't all that relevant,
 * so I'd be happy to filter out 42 of them. How do I do this?<br>
 * A: This is a bit harder than just adding variants. First, you should invoke
 * {@link CompletionResultSet#runRemainingContributors(CompletionParameters, Consumer)}.
 * The consumer you provide should pass all the lookup elements to the {@link CompletionResultSet}
 * given to you, except for the ones you wish to filter out. Be careful: it's too easy to break completion this way. Since you've
 * ordered to invoke remaining contributors yourself, they won't be invoked automatically after yours finishes (see
 * {@link CompletionResultSet#stopHere()} and {@link CompletionResultSet#isStopped()}).
 * Calling {@link CompletionResultSet#stopHere()} explicitly will stop other contributors (which happened to be loaded after yours)
 * from execution, and the user will never see their so useful and precious completion variants, so please be careful with this method.<p>
 *
 * Q: How are lookup elements sorted?<br>
 * A: Basically in lexicographic order, ascending, by lookup string ({@link LookupElement#getLookupString()}).
 * Also there's a number of "weigher" extensions under "completion" key (see {@link CompletionWeigher}) that bubble up the most relevant
 * items. To control lookup elements order you may implement {@link CompletionWeigher} or use {@link PrioritizedLookupElement}.<br>
 * To debug the order of the completion items use '<code>Dump lookup element weights to log</code>' action when the completion lookup is
 * shown (Ctrl+Alt+Shift+W / Cmd+Alt+Shift+W), the action also copies the debug info to the the Clipboard.
 * <p>
 *
 * Q: My completion is not working! How do I debug it?<br>
 * A: One source of common errors is that the pattern you gave to {@link #extend(CompletionType, ElementPattern, CompletionProvider)} method
 * may be incorrect. To debug this problem you can still override {@link #fillCompletionVariants(CompletionParameters, CompletionResultSet)} in
 * your contributor, make it only call its super and put a breakpoint there.<br>
 * If you want to know which contributor added a particular lookup element, the best place for a breakpoint will be
 * {@link CompletionService#performCompletion(CompletionParameters, Consumer)}. The consumer passed there
 * is the 'final' consumer, it will pass your lookup elements directly to the lookup.<br>
 * If your contributor isn't even invoked, probably there was another contributor that said 'stop' to the system, and yours happened to be ordered after
 * that contributor. To test this hypothesis, put a breakpoint to
 * {@link CompletionService#getVariantsFromContributors(CompletionParameters, CompletionContributor, Consumer)},
 * to the 'return false' line.<p>
 *
 * @author peter
 */
public abstract class CompletionContributor {

  private final MultiMap<CompletionType, Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>>> myMap =
    new MultiMap<>();

  public final void extend(@Nullable CompletionType type,
                           @NotNull final ElementPattern<? extends PsiElement> place, CompletionProvider<CompletionParameters> provider) {
    myMap.putValue(type, new Pair<>(place, provider));
  }

  /**
   * The main contributor method that is supposed to provide completion variants to result, based on completion parameters.
   * The default implementation looks for {@link CompletionProvider}s you could register by
   * invoking {@link #extend(CompletionType, ElementPattern, CompletionProvider)} from your contributor constructor,
   * matches the desired completion type and {@link ElementPattern} with actual ones, and, depending on it, invokes those
   * completion providers.<p>
   *
   * If you want to implement this functionality directly by overriding this method, the following is for you.
   * Always check that parameters match your situation, and that completion type ({@link CompletionParameters#getCompletionType()}
   * is of your favourite kind. This method is run inside a read action. If you do any long activity non-related to PSI in it, please
   * ensure you call {@link com.intellij.openapi.progress.ProgressManager#checkCanceled()} often enough so that the completion process
   * can be cancelled smoothly when the user begins to type in the editor.
   */
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result) {
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>> pair : myMap.get(parameters.getCompletionType())) {
      ProgressManager.checkCanceled();
      final ProcessingContext context = new ProcessingContext();
      if (pair.first.accepts(parameters.getPosition(), context)) {
        pair.second.addCompletionVariants(parameters, context, result);
        if (result.isStopped()) {
          return;
        }
      }
    }
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<CompletionParameters>> pair : myMap.get(null)) {
      final ProcessingContext context = new ProcessingContext();
      if (pair.first.accepts(parameters.getPosition(), context)) {
        pair.second.addCompletionVariants(parameters, context, result);
        if (result.isStopped()) {
          return;
        }
      }
    }
  }

  /**
   * Invoked before completion is started. Is used mainly for determining custom offsets in editor, and to change default dummy identifier.
   */
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
  }

  /**
   * @deprecated use {@link CompletionResultSet#addLookupAdvertisement(String)}
   * @return text to be shown at the bottom of lookup list
   */
  @Nullable
  public String advertise(@NotNull CompletionParameters parameters) {
    return null;
  }

  /**
   *
   * @return hint text to be shown if no variants are found, typically "No suggestions"
   */
  @Nullable
  public String handleEmptyLookup(@NotNull CompletionParameters parameters, final Editor editor) {
    return null;
  }

  /**
   * Called when the completion is finished quickly, lookup hasn't been shown and gives possibility to autoinsert some item (typically - the only one).
   */
  @Nullable
  public AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    return null;
  }

  /**
   * Allow autoPopup to appear after custom symbol
   */
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    return false;
  }

  /**
   * Invoked in a read action in parallel to the completion process. Used to calculate the replacement offset
   * (see {@link CompletionInitializationContext#setReplacementOffset(int)})
   * if it takes too much time to spend it in {@link #beforeCompletion(CompletionInitializationContext)},
   * e.g. doing {@link com.intellij.psi.PsiFile#findReferenceAt(int)}
   *
   * Guaranteed to be invoked before any lookup element is selected
   *
   * @param context context
   */
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
  }

  /**
   * @return String representation of action shortcut. Useful while advertising something
   * @see #advertise(CompletionParameters)
   */
  @NotNull
  protected static String getActionShortcut(@NonNls @NotNull final String actionId) {
    return KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(actionId));
  }

  @NotNull
  public static List<CompletionContributor> forParameters(@NotNull final CompletionParameters parameters) {
    return ReadAction.compute(() -> {
      PsiElement position = parameters.getPosition();
      return forLanguageHonorDumbness(PsiUtilCore.getLanguageAtOffset(position.getContainingFile(), parameters.getOffset()), position.getProject());
    });
  }

  @NotNull
  public static List<CompletionContributor> forLanguage(@NotNull Language language) {
    return INSTANCE.forKey(language);
  }

  @NotNull
  public static List<CompletionContributor> forLanguageHonorDumbness(@NotNull Language language, @NotNull Project project) {
    return DumbService.getInstance(project).filterByDumbAwareness(forLanguage(language));
  }

  private static final LanguageExtension<CompletionContributor> INSTANCE = new LanguageExtension<CompletionContributor>("com.intellij.completion.contributor") {
    @NotNull
    @Override
    protected List<CompletionContributor> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
      return buildExtensions(getAllBaseLanguageIdsWithAny(key));
    }
  };

}
