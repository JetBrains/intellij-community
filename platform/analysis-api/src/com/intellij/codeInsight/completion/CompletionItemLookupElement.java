// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.modcompletion.CompletionItem;
import com.intellij.modcompletion.CompletionItemPresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A wrapper around {@link CompletionItem} that adapts it to {@link LookupElement}.
 */
@NotNullByDefault
@ApiStatus.Internal
public final class CompletionItemLookupElement extends LookupElement {
  private final CompletionItem item;

  CompletionItemLookupElement(CompletionItem item) {
    this.item = item;
  }
  
  public CompletionItem item() {
    return item;
  }

  @Override
  public String getLookupString() {
    return item.mainLookupString();
  }

  @Override
  public @Unmodifiable Set<String> getAllLookupStrings() {
    Set<String> strings = item.additionalLookupStrings();
    return strings.isEmpty() ? Set.of(item.mainLookupString()) :
           Stream.concat(Stream.of(item.mainLookupString()), strings.stream()).collect(Collectors.toSet());
  }

  @Override
  public Object getObject() {
    return item.contextObject();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    CompletionItemPresentation itemPresentation = item.presentation();
    // TODO: apply styles when possible
    presentation.setItemText(itemPresentation.mainText().toText());
    presentation.setTailText(" (MC)");
    presentation.setTypeText(itemPresentation.detailText().toText());
  }

  @Override
  public boolean requiresCommittedDocuments() {
    return false;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    throw new UnsupportedOperationException("Should not be called: unwrap the element via item() method and process it as a ModCommand");
  }

  @Override
  public String toString() {
    return "Adapter for " + item;
  }
}
