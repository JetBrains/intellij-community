// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionWithAny;
import com.intellij.modcompletion.CompletionItemProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * <b>Completion FAQ</b><p>
 *
 * <b>Q: How do I implement code completion?</b><br>
 * A: Define a {@code com.intellij.completion.contributor} extension of type {@link CompletionContributor}.
 * Or, if the place you want to complete in contains a {@link PsiReference}, return the variants
 * you want to suggest from its {@link PsiReference#getVariants()} method as {@link String}s,
 * {@link PsiElement}s, or better {@link LookupElement}s.<p>
 *
 * <b>Q: OK, but what to do with CompletionContributor?</b><br>
 * A: There are two ways. The easier and preferred one is to provide constructor in your contributor and register completion providers there:
 * {@link #extend(CompletionType, ElementPattern, CompletionProvider)}.<br>
 * A more generic way is to override default {@link #fillCompletionVariants(CompletionParameters, CompletionResultSet)} implementation
 * and provide your own.
 * It's easier to debug but harder to write.<p>
 *
 * <b>Q: How do I get an automatic lookup element filtering by prefix?</b><br>
 * A: When you return variants from reference ({@link PsiReference#getVariants()}), the filtering will be done
 * automatically, with prefix taken as the reference text from its start ({@link PsiReference#getRangeInElement()}) to
 * the caret position.
 * In {@link CompletionContributor} you will be given a {@link CompletionResultSet}
 * which will match {@link LookupElement}s against its prefix matcher {@link CompletionResultSet#getPrefixMatcher()}.
 * If the default prefix calculated by the IDE doesn't satisfy you, you can get another result set via
 * {@link CompletionResultSet#withPrefixMatcher(PrefixMatcher)} and feed your lookup elements to the latter.
 * It's one of the item's lookup strings ({@link LookupElement#getAllLookupStrings()} that is matched against prefix matcher.<p>
 *
 * <b>Q: How do I plug into those funny texts below the items in shown lookup?</b><br>
 * A: Use {@link CompletionResultSet#addLookupAdvertisement(String)}.<p>
 *
 * <b>Q: How do I change the text that gets shown when there are no suitable variants at all?</b><br>
 * A: Use {@link CompletionContributor#handleEmptyLookup(CompletionParameters, Editor)}.
 * Remember to check whether you are in correct place (see {@link CompletionParameters}).<p>
 *
 * <b>Q: How do I affect a lookup element's appearance (icon, text attributes, etc.)?</b><br>
 * A: See {@link LookupElement#renderElement(LookupElementPresentation)}.<p>
 *
 * <b>Q: I'm not satisfied that completion just inserts the item's lookup string on item selection. How to make it write something else?</b><br>
 * A: See {@link LookupElement#handleInsert(InsertionContext)}.<p>
 *
 * <b>Q: What if I select an item with a TAB key?</b><br>
 * A: Semantics is that the identifier that you're standing inside gets removed completely, and then the lookup string is inserted. You can change
 * the deleting range end offset, do it in {@link CompletionContributor#beforeCompletion(CompletionInitializationContext)}
 * by putting new offset to {@link CompletionInitializationContext#getOffsetMap()} as {@link CompletionInitializationContext#IDENTIFIER_END_OFFSET}.<p>
 *
 * <b>Q: I know more about my environment than the IDE does, and I can swear that those 239 variants it suggests to me in some places aren't all that relevant,
 * so I'd be happy to filter out 42 of them. How do I do this?</b><br>
 * A: This is a bit harder than just adding variants. First, you should invoke
 * {@link CompletionResultSet#runRemainingContributors(CompletionParameters, Consumer)}.
 * The consumer you provide should pass all the lookup elements to the {@link CompletionResultSet}
 * given to you, except for the ones you wish to filter out. Be careful: it's too easy to break completion this way. Since you've
 * ordered to invoke remaining contributors yourself, they won't be invoked automatically after yours finishes (see
 * {@link CompletionResultSet#stopHere()} and {@link CompletionResultSet#isStopped()}).
 * Calling {@link CompletionResultSet#stopHere()} explicitly will stop other contributors (which happened to be loaded after yours)
 * from execution, and the user will never see their such useful and precious completion variants, so please be careful with this method.<p>
 *
 * <b>Q: How are lookup elements sorted?</b><br>
 * A: Basically in lexicographic order, ascending, by lookup string ({@link LookupElement#getLookupString()}).
 * Also, there are a number of "weigher" extensions under "completion" key (see {@link CompletionWeigher}) that bubble up the most relevant
 * items. To control lookup elements order you may implement {@link CompletionWeigher} or use {@link PrioritizedLookupElement}.<br>
 * To debug the order of the completion items use '<code>Dump lookup element weights to log</code>' action when the completion lookup is
 * shown (Ctrl+Alt+Shift+W / Cmd+Alt+Shift+W), the action also copies the debug info to the Clipboard.<p>
 *
 * <b>Q: Elements in the lookup are sorted unexpectedly, the weights I provide are not honored, why?</b><br>
 * A: To be more responsive, when the first lookup elements are produced, the completion infrastructure waits for some short time
 * and then displays the lookup with whatever items are ready. After that, few of the most relevant displayed items
 * are considered "frozen" and not re-sorted anymore, to avoid changes around the selected item that the user already sees
 * and can interact with. Even if new, more relevant items are added, they won't make it to the top of the list anymore.
 * Therefore, you should try to create the most relevant items as early as possible. If you can't reliably produce
 * most relevant items first, you could also return all your items in batch via {@link CompletionResultSet#addAllElements} to ensure
 * that this batch is all sorted and displayed together.<p>
 *
 * <b>Q: My completion is not working! How do I debug it?</b><br>
 * A: One source of common errors is that the pattern you gave to {@link #extend(CompletionType, ElementPattern, CompletionProvider)} method
 * may be incorrect. To debug this problem you can still override {@link #fillCompletionVariants(CompletionParameters, CompletionResultSet)} in
 * your contributor, make it only call its super method, and put a breakpoint there.<br>
 * If you want to know which contributor added a particular lookup element, the best place for a breakpoint will be
 * {@link CompletionService#performCompletion(CompletionParameters, Consumer)}. The consumer passed there
 * is the 'final' consumer, it will pass your lookup elements directly to the lookup.<br>
 * If your contributor isn't even invoked, probably there was another contributor that said 'stop' to the system, and yours happened to be ordered after
 * that contributor. To test this hypothesis, put a breakpoint to
 * {@link CompletionService#getVariantsFromContributors(CompletionParameters, CompletionContributor, PrefixMatcher, Consumer)},
 * to the 'return' line.<p>
 *
 * <b>Q: My completion contributor has to get its results from far away (e.g., blocking I/O or internet). How do I do that?</b><br>
 * A: To avoid UI freezes, your completion thread should be cancellable at all times.
 * So it's a bad idea to do blocking requests from it directly since it runs in a read action,
 * and if it can't do {@link ProgressManager#checkCanceled()} and therefore any attempt to type in a document will freeze the UI.
 * A common solution is to start another thread, without read action, for such blocking requests
 * and wait for their results in the completion thread.
 * You can use {@link com.intellij.openapi.application.ex.ApplicationUtil#runWithCheckCanceled} for that.<p>
 *
 * <b>Q: How can I trigger showing completion popup programmatically?</b><br>
 * A: See {@link com.intellij.codeInsight.AutoPopupController}.<p>
 *
 * <b>Q: The suggestion popup hides when I type some exotic character,
 * but I want completion to keep going, matching against the typed character.</b><br>
 * A: See {@link com.intellij.codeInsight.lookup.CharFilter#acceptChar(char, int, com.intellij.codeInsight.lookup.Lookup)}.
 */
@SuppressWarnings("JavadocReference")
public abstract class CompletionContributor implements PossiblyDumbAware {
  public static final ExtensionPointName<CompletionContributorEP> EP = new ExtensionPointName<>("com.intellij.completion.contributor");

  private final MultiMap<CompletionType, ProviderWithPattern> myMap = new MultiMap<>();

  public final void extend(@Nullable CompletionType type,
                           @NotNull ElementPattern<? extends PsiElement> place,
                           @NotNull CompletionProvider<CompletionParameters> provider) {
    myMap.putValue(type, new ProviderWithPattern(place, provider));
  }

  /**
   * The main contributor method that is supposed to provide completion variants to the result, based on completion parameters.
   * The default implementation looks for {@link CompletionProvider}s you could register by
   * invoking {@link #extend(CompletionType, ElementPattern, CompletionProvider)} from your contributor constructor,
   * matches the desired completion type and {@link ElementPattern} with actual ones, and, depending on it, invokes those
   * completion providers.<p>
   * <p>
   * If you want to implement this functionality directly by overriding this method, the following is for you.
   * Always check that parameters match your situation, and that completion type ({@link CompletionParameters#getCompletionType()}
   * is of your favourite kind. This method is run inside a read action. If you do any long activity non-related to PSI in it, please
   * ensure you call {@link ProgressManager#checkCanceled()} often enough so that the completion process
   * can be canceled smoothly when the user begins to type in the editor.
   */
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    Iterable<ProviderWithPattern> providers = getProviders(parameters);
    for (ProviderWithPattern provider : providers) {
      ProgressManager.checkCanceled();
      provider.processCandidates(parameters, result);
      if (result.isStopped()) {
        return;
      }
    }
  }

  private @NotNull Iterable<ProviderWithPattern> getProviders(@NotNull CompletionParameters parameters) {
    Collection<ProviderWithPattern> providers1 = myMap.get(parameters.getCompletionType());
    Collection<ProviderWithPattern> providers2 = myMap.get(null);
    Iterable<ProviderWithPattern> allProviders = ContainerUtil.concat(providers1, providers2);
    return allProviders;
  }

  /**
   * Invoked before completion is started.
   * It is used mainly for determining custom offsets in the editor and to change the default dummy identifier.
   */
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
  }

  /**
   * @return text to be shown at the bottom of the lookup list
   * @deprecated use {@link CompletionResultSet#addLookupAdvertisement(String)}
   */
  @Deprecated(forRemoval = true)
  public @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String advertise(@NotNull CompletionParameters parameters) {
    return null;
  }

  /**
   *
   * @return hint text to be shown if no variants are found, typically "No suggestions"
   */
  public @Nullable @NlsContexts.HintText String handleEmptyLookup(@NotNull CompletionParameters parameters, @NotNull Editor editor) {
    return null;
  }

  /**
   * Called when the completion is finished quickly, lookup hasn't been shown and gives the possibility to auto-insert some item (typically - the only one).
   */
  public @Nullable AutoCompletionDecision handleAutoCompletionPossibility(@NotNull AutoCompletionContext context) {
    return null;
  }

  /**
   * @deprecated Don't use this method, because {@code position} can come from uncommitted PSI and be totally unrelated to the code being currently in the document/editor.
   * Please consider using {@link com.intellij.codeInsight.editorActions.TypedHandlerDelegate#checkAutoPopup} instead.
   */
  @Deprecated
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    return false;
  }

  /**
   * Invoked in a read action in parallel to the completion process. Used to calculate the replacement offset
   * (see {@link CompletionInitializationContext#setReplacementOffset(int)})
   * if it takes too much time to spend it in {@link #beforeCompletion(CompletionInitializationContext)},
   * e.g., doing {@link com.intellij.psi.PsiFile#findReferenceAt(int)}
   * <p>
   * Guaranteed to be invoked before any lookup element is selected
   *
   * @param context context
   */
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
  }

  public static @NotNull List<CompletionContributor> forParameters(@NotNull CompletionParameters parameters) {
    return ReadAction.compute(() -> {
      PsiElement position = parameters.getPosition();
      return forLanguageHonorDumbness(PsiUtilCore.getLanguageAtOffset(position.getContainingFile(), parameters.getOffset()),
                                      position.getProject());
    });
  }

  public static @NotNull List<CompletionContributor> forLanguage(@NotNull Language language) {
    List<CompletionContributor> contributors = INSTANCE.forKey(language);
    if (Registry.is("ide.completion.modcommand", false)) {
      contributors =
        ContainerUtil.concat(ContainerUtil.map(CompletionItemProvider.forLanguage(language), CompletionItemContributor::new), contributors);
    }
    return contributors;
  }

  public static @NotNull List<CompletionContributor> forLanguageHonorDumbness(@NotNull Language language, @NotNull Project project) {
    return DumbService.getInstance(project).filterByDumbAwareness(forLanguage(language));
  }

  private static final LanguageExtension<CompletionContributor> INSTANCE = new LanguageExtensionWithAny<>(EP.getName());

  private record ProviderWithPattern(
    @NotNull ElementPattern<? extends PsiElement> pattern,
    @NotNull CompletionProvider<CompletionParameters> provider
  ) {
    void processCandidates(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      ProcessingContext context = new ProcessingContext();
      if (pattern.accepts(parameters.getPosition(), context)) {
        provider.addCompletionVariants(parameters, context, result);
      }
    }
  }
}
