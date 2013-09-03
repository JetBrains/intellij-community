package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class PsiSoftReferenceDecorator extends PsiDelegateReference {

  private final boolean mySoft;

  public PsiSoftReferenceDecorator(@NotNull PsiReference delegate, boolean soft) {
    super(delegate);
    mySoft = soft;
  }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  public static PsiReference[] create(PsiReference[] refs, boolean soft) {
    PsiReference[] res = new PsiReference[refs.length];

    for (int i = 0; i < refs.length; i++) {
      res[i] = new PsiSoftReferenceDecorator(refs[i], soft);
    }

    return res;
  }
}
