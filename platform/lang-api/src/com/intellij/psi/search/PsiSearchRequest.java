package com.intellij.psi.search;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class PsiSearchRequest {

  public static SingleRequest elementsWithWord(@NotNull SearchScope searchScope,
                                 @NotNull String word,
                                 short searchContext,
                                 boolean caseSensitive, @NotNull TextOccurenceProcessor processor) {
    return new SingleRequest(searchScope, word, searchContext, caseSensitive, processor);
  }

  public static ComplexRequest composite() {
    return new ComplexRequest();
  }

  public static CustomRequest custom(Runnable searchAction) {
    return new CustomRequest(searchAction);
  }

  public static class SingleRequest extends PsiSearchRequest {
    public final SearchScope searchScope;
    public final String word;
    public final short searchContext;
    public final boolean caseSensitive;
    public final TextOccurenceProcessor processor;

    private SingleRequest(@NotNull SearchScope searchScope,
                         @NotNull String word,
                         short searchContext,
                         boolean caseSensitive,
                         @NotNull TextOccurenceProcessor processor) {

      this.searchScope = searchScope;
      this.word = word;
      this.searchContext = searchContext;
      this.caseSensitive = caseSensitive;
      this.processor = processor;
    }
  }
  
  public static class ComplexRequest extends PsiSearchRequest {
    private final List<PsiSearchRequest> myComponents = new ArrayList<PsiSearchRequest>();

    private ComplexRequest() {
    }

    public void addRequest(PsiSearchRequest request) {
      if (request instanceof ComplexRequest) {
        myComponents.addAll(((ComplexRequest)request).getConstituents());
      } else {
        myComponents.add(request);
      }
    }

    public List<PsiSearchRequest> getConstituents() {
      return myComponents;
    }
  }

  public static class CustomRequest extends PsiSearchRequest {
    public final Runnable searchAction;

    private CustomRequest(Runnable searchAction) {
      this.searchAction = searchAction;
    }
  }
  
  
}
