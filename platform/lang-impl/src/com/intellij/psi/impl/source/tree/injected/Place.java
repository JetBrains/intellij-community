package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
*/
public class Place extends SmartList<PsiLanguageInjectionHost.Shred> {
  private volatile PsiFile myInjectedPsi;

  Place(@NotNull List<PsiLanguageInjectionHost.Shred> shreds, PsiFile injectedPsi) {
    super(shreds);
    setInjectedPsi(injectedPsi);
  }

  public void setInjectedPsi(PsiFile injectedPsi) {
    myInjectedPsi = injectedPsi;
  }

  public PsiFile getInjectedPsi() {
    return myInjectedPsi;
  }

  public boolean isValid() {
    if (myInjectedPsi != null && !myInjectedPsi.isValid()) {
      return false;
    }
    for (PsiLanguageInjectionHost.Shred shred : this) {
      if (!shred.isValid()) {
        return false;
      }
    }
    return true;
  }
}
