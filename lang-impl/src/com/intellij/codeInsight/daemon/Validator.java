package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 14, 2005
 * Time: 10:53:20 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Validator<T extends PsiElement> {
  interface ValidationHost {
    int ERROR = 1;
    int WARNING = 0;
    int INFO = 2;

    void addMessage(PsiElement context, String message, int type);
  }
  

  void validate(@NotNull T context,@NotNull ValidationHost host);
}
