package com.intellij.compilerOutputIndex.impl.bigram;

import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignatureChain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author Dmitry Batkovich
*/
class SimpleBigramsExtractor {
  private final Map<String, MethodIncompleteSignature> myHolder = new HashMap<String, MethodIncompleteSignature>();
  private final BigramMethodIncompleteSignatureProcessor myProcessor;

  public SimpleBigramsExtractor(final BigramMethodIncompleteSignatureProcessor processor) {
    myProcessor = processor;
  }

  public void addChain(final MethodIncompleteSignatureChain chain) {
    if (chain.isEmpty()) {
      return;
    }
    final MethodIncompleteSignature firstInvocation = chain.getFirstInvocation();
    assert firstInvocation != null;
    final MethodIncompleteSignature head = firstInvocation.isStatic() ? null : myHolder.get(firstInvocation.getOwner());
    for (final Bigram<MethodIncompleteSignature> bigram : toBigrams(head, chain)) {
      myProcessor.process(bigram);
    }
    final MethodIncompleteSignature lastInvocation = chain.getLastInvocation();
    assert lastInvocation != null;
    myHolder.put(lastInvocation.getReturnType(), lastInvocation);
  }

  private static Collection<Bigram<MethodIncompleteSignature>> toBigrams(final @Nullable MethodIncompleteSignature head,
                                                                         final @NotNull MethodIncompleteSignatureChain chain) {
    MethodIncompleteSignature currentLast = null;
    if (head != null) {
      currentLast = head;
    }
    final List<Bigram<MethodIncompleteSignature>> bigrams = new ArrayList<Bigram<MethodIncompleteSignature>>(chain.size());
    for (final MethodIncompleteSignature current : chain.list()) {
      if (currentLast != null) {
        bigrams.add(new Bigram<MethodIncompleteSignature>(currentLast, current));
      }
      currentLast = current;
    }
    return bigrams;
  }

  public interface BigramMethodIncompleteSignatureProcessor {
    void process(Bigram<MethodIncompleteSignature> bigram);
  }
}
