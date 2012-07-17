package com.intellij.psi.codeStyle.rearranger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Encapsulates language-specific rearrangement logic.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/16/12 3:23 PM
 * @param <E>   entry type
 * @param <R>   rule type
 */
public interface RearrangerFacade<E extends RearrangerEntry> {

  ExtensionPointName<RearrangerFacade> EP_NAME = ExtensionPointName.create("com.intellij.rearranger.facade");

  /**
   * Allows to build rearranger-interested data for the given element.
   *
   * @param root  root element which children should be parsed for the rearrangement
   * @param range target offsets range to use for filtering given root's children
   * @return given root's children that are subject for further rearrangement
   */
  @NotNull
  Collection<E> parse(@NotNull PsiElement root, @NotNull TextRange range);
  
  /**
   * Asks to sort given entries matched against the given rule. Given list defines a default order to be used.
   * 
   * @param entries  entries matched against the given rule
   * @param rule     target rule
   */
  void sort(@NotNull List<E> entries, @NotNull RearrangerRule rule);
}
