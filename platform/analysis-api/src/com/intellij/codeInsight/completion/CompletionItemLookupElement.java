// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A wrapper around {@link ModCompletionItem} that adapts it to {@link LookupElement}.
 */
@NotNullByDefault
@ApiStatus.Internal
public final class CompletionItemLookupElement extends LookupElement implements ReportingClassSubstitutor {
  private final ModCompletionItem myItem;
  private volatile @Nullable ModCommand myCachedCommand;
  private final AutoCompletionPolicy myPolicy;

  public CompletionItemLookupElement(ModCompletionItem item) {
    this(item, item.autoCompletionPolicy());
  }
  
  private CompletionItemLookupElement(ModCompletionItem item, AutoCompletionPolicy policy) {
    myItem = item;
    myPolicy = policy;
  }

  /**
   * @param policy auto-completion policy
   * @return a lookup element with an overridden auto-completion policy
   */
  public CompletionItemLookupElement withAutoCompletionPolicy(AutoCompletionPolicy policy) {
    return myPolicy == policy ? this : new CompletionItemLookupElement(myItem, policy);
  }

  /**
   * @return the completion item wrapped by this element.
   */
  public ModCompletionItem item() {
    return myItem;
  }

  /**
   * Returns the command to perform the completion (e.g., insert the lookup string). Should be launched in a background read action.
   * 
   * @param actionContext action context where the completion is performed. 
   *                      The selection range denotes the prefix text inserted during the current completion session.
   *                      The command must ignore it, as at the time it will be applied, the selection range will be deleted. 
   * @param insertionContext an insertion context, which describes how exactly the user invoked the completion
   * @return the command to perform the completion (e.g., insert the lookup string).
   * The command must assume that the selection range is already deleted. May return the cached command without recomputing.
   * @see ModCompletionItem#perform(ActionContext, ModCompletionItem.InsertionContext) 
   */
  @RequiresReadLock
  public ModCommand computeCommand(ActionContext actionContext, ModCompletionItem.InsertionContext insertionContext) {
    if (!insertionContext.equals(ModCompletionItem.DEFAULT_INSERTION_CONTEXT)) {
      return myItem.perform(actionContext, insertionContext);
    }
    ModCommand command = getCachedCommand(actionContext, insertionContext);
    if (command != null) {
      return command;
    }
    command = myItem.perform(actionContext, insertionContext);
    myCachedCommand = command;
    return command;
  }

  /**
   * Cached command in case if it was already computed and stored before for the given context.
   * May be used instead of {@link #computeCommand(ActionContext, ModCompletionItem.InsertionContext)} to optimize performance.
   * 
   * @param actionContext action context where the completion is performed. 
   *                      The selection range denotes the prefix text inserted during the current completion session.
   *                      The command must ignore it, as at the time it will be applied, the selection range will be deleted. 
   * @param insertionContext an insertion context, which describes how exactly the user invoked the completion
   * @return the command to perform the completion (e.g., insert the lookup string); null if it's not yet cached.
   * @see #computeCommand(ActionContext, ModCompletionItem.InsertionContext)
   */
  public @Nullable ModCommand getCachedCommand(ActionContext actionContext, ModCompletionItem.InsertionContext insertionContext) {
    ModCommand command = myCachedCommand;
    if (command == null || !insertionContext.equals(ModCompletionItem.DEFAULT_INSERTION_CONTEXT)) {
      return null;
    }
    if (isApplicableToContext(command, actionContext)) {
      return command;
    }
    return null;
  }

  @Override
  public Class<?> getSubstitutedClass() {
    return myItem.getClass();
  }

  private static boolean isApplicableToContext(ModCommand command, ActionContext context) {
    VirtualFile file = context.file().getVirtualFile();
    String text = context.file().getFileDocument().getText();
    TextRange selection = context.selection();
    if (!selection.isEmpty()) {
      text = text.substring(0, selection.getStartOffset())
             + text.substring(selection.getEndOffset());
    }
    for (ModCommand subCommand : command.unpack()) {
      if (subCommand instanceof ModUpdateFileText update) {
        if (update.file().equals(file)) {
          return update.oldText().equals(text);
        }
      }
    }
    return false;
  }

  @Override
  public boolean isValid() {
    return myItem.isValid();
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return myPolicy;
  }

  @Override
  public String getLookupString() {
    return myItem.mainLookupString();
  }

  @Override
  public @Unmodifiable Set<String> getAllLookupStrings() {
    Set<String> strings = myItem.additionalLookupStrings();
    return strings.isEmpty() ? Set.of(myItem.mainLookupString()) :
           Stream.concat(Stream.of(myItem.mainLookupString()), strings.stream()).collect(Collectors.toSet());
  }

  @Override
  public Object getObject() {
    return myItem.contextObject();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    ModCompletionItemPresentation itemPresentation = myItem.presentation();
    // TODO: apply styles when possible
    MarkupText mainText = itemPresentation.mainText();
    List<MarkupText.Fragment> fragments = mainText.fragments();
    String tailText = "";
    boolean gray = false;
    if (!fragments.isEmpty()) {
      if (fragments.size() > 1) {
        MarkupText.Fragment last = fragments.getLast();
        if (last.kind() == MarkupText.Kind.GRAYED || last.kind() == MarkupText.Kind.NORMAL) {
          gray = last.kind() == MarkupText.Kind.GRAYED;
          tailText = last.text();
          fragments = fragments.subList(0, fragments.size() - 1);
        }
      }
      presentation.setItemText(StringUtil.join(fragments, MarkupText.Fragment::text, ""));
      MarkupText.Fragment onlyFragment = ContainerUtil.getOnlyItem(fragments);
      if (onlyFragment != null) {
        switch (onlyFragment.kind()) {
          case STRONG -> presentation.setItemTextBold(true);
          case EMPHASIZED -> presentation.setItemTextItalic(true);
          case STRIKEOUT -> presentation.setStrikeout(true);
          case ERROR -> presentation.setItemTextForeground(JBColor.RED);
        }
      }
    }
    presentation.setIcon(itemPresentation.mainIcon());
    presentation.setTailText(tailText, gray);
    presentation.setTypeText(itemPresentation.detailText().toText(), itemPresentation.detailIcon());
  }

  @Override
  public boolean requiresCommittedDocuments() {
    return false;
  }

  /**
   * Throws UnsupportedOperationException. Should not be called directly. Instead, to execute the command, 
   * use {@link #computeCommand(ActionContext, ModCompletionItem.InsertionContext)}.
   */
  @Override
  public void handleInsert(InsertionContext context) {
    throw new UnsupportedOperationException("Should not be called: unwrap the element via item() method and process it as a ModCommand");
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CompletionItemLookupElement element && myItem.equals(element.myItem);
  }

  @Override
  public int hashCode() {
    return myItem.hashCode();
  }

  @Override
  public String toString() {
    return "Adapter for: " + myItem;
  }
}
