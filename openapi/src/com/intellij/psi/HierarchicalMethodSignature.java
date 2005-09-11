package com.intellij.psi;

import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author ven
 */
public class HierarchicalMethodSignature extends MethodSignatureBackedByPsiMethod {
  List<HierarchicalMethodSignature> mySupers;

  public HierarchicalMethodSignature(final MethodSignatureBackedByPsiMethod signature) {
    super(signature.getMethod(), signature.getSubstitutor(), signature.isRaw(), signature.isInGenericContext(),
          signature.getParameterTypes(), signature.getTypeParameters());
  }

  public List<HierarchicalMethodSignature> getSuperSignatures() {
    return Collections.unmodifiableList(mySupers == null ? Collections.EMPTY_LIST : mySupers);
  }

  public void addSuperSignature(HierarchicalMethodSignature superSignatureHierarchical) {
    if (mySupers == null) mySupers = new ArrayList<HierarchicalMethodSignature>(2);
    mySupers.add(superSignatureHierarchical);
  }
}
