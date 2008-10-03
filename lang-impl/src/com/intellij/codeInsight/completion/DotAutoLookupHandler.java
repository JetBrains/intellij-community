package com.intellij.codeInsight.completion;

public class DotAutoLookupHandler extends CodeCompletionHandlerBase{

  public DotAutoLookupHandler() {
    super(CompletionType.BASIC);
  }

  protected boolean mayAutocompleteOnInvocation() {
    return false;
  }

  protected boolean isAutocompleteCommonPrefixOnInvocation() {
    return false;
  }

  protected void handleEmptyLookup(CompletionContext context, final CompletionParameters parameters,
                                   final CompletionProgressIndicator indicator){
  }
}
