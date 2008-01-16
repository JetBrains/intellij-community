/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class DummyHolderFactory  {
  private static HolderFactory INSTANCE = new DefaultFactory();

  private DummyHolderFactory() {}

  public static void setFactory(HolderFactory factory) {
    INSTANCE = factory;
  }

  public static DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    return INSTANCE.createHolder(manager, contentElement, context);
  }

  public static DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    return INSTANCE.createHolder(manager, table, validity);
  }

  public static DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
    return INSTANCE.createHolder(manager, context);
  }

  public static DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    return INSTANCE.createHolder(manager, contentElement, context, table);
  }

  public static DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    return INSTANCE.createHolder(manager, context, table);
  }

  public static DummyHolder createHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
    return INSTANCE.createHolder(manager, table, language);
  }

  private static class DefaultFactory implements HolderFactory {
    public DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
      return new DummyHolder(manager, contentElement, context);
    }

    public DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
      return new DummyHolder(manager, table, validity);
    }

    public DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
      return new DummyHolder(manager, context);
    }

    public DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
      return new DummyHolder(manager, contentElement, context, table);
    }

    public DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
      return new DummyHolder(manager, context, table);
    }

    public DummyHolder createHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
      return new DummyHolder(manager, table, language);
    }
  }
}