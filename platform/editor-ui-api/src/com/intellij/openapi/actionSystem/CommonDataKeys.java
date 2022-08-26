// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.lang.Language;
import com.intellij.model.Symbol;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * Common data keys used as parameter for {@link AnActionEvent#getData(DataKey)} in {@link AnAction#update(AnActionEvent)}/{@link AnAction#actionPerformed(AnActionEvent)} implementations.
 *
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 * @see com.intellij.openapi.actionSystem.LangDataKeys
 */
public class CommonDataKeys {

  public static final DataKey<Project> PROJECT = DataKey.create("project");

  /**
   * Returns currently focused editor instance.
   *
   * @see #HOST_EDITOR
   * @see #EDITOR_EVEN_IF_INACTIVE
   */
  public static final DataKey<Editor> EDITOR = DataKey.create("editor");

  /**
   * Returns reference to host editor instance, in case {@link #EDITOR} key is referring to an injected editor.
   */
  public static final DataKey<Editor> HOST_EDITOR = DataKey.create("host.editor");

  /**
   * Returns caret instance (in host or injected editor, depending on context).
   */
  public static final DataKey<Caret> CARET = DataKey.create("caret");

  /**
   * Returns editor even if focus currently is in find bar.
   */
  public static final DataKey<Editor> EDITOR_EVEN_IF_INACTIVE = DataKey.create("editor.even.if.inactive");

  /**
   * Returns {@link Navigatable} instance.
   * <p/>
   * If {@link DataContext} has a value for {@link #PSI_ELEMENT} key which implements {@link Navigatable} it will automatically
   * return it for this key if explicit value isn't provided.
   */
  public static final DataKey<Navigatable> NAVIGATABLE = DataKey.create("Navigatable");

  /**
   * Returns several navigatables, e.g. if several elements are selected in a component.
   * <p/>
   * Note that if {@link DataContext} has a value for {@link #NAVIGATABLE} key it will automatically return single-element array for this
   * key if explicit value isn't provided so there is no need to perform such wrapping by hand.
   */
  public static final DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create("NavigatableArray");

  /**
   * Returns {@link VirtualFile} instance.
   * <p/>
   * Note that if {@link DataContext} has a value for {@link #PSI_FILE} key it will automatically return the corresponding {@link VirtualFile}
   * for this key if explicit value isn't provided. Also it'll return the containing file if a value for {@link #PSI_ELEMENT} key is provided.
   */
  public static final DataKey<VirtualFile> VIRTUAL_FILE = DataKey.create("virtualFile");

  /**
   * Returns several {@link VirtualFile} instances, e.g. if several elements are selected in a component.
   * <p/>
   * Note that if {@link DataContext} doesn't have an explicit value for this key it will automatically collect {@link VirtualFile} instances
   * corresponding to values provided for {@link #VIRTUAL_FILE}, {@link #PSI_FILE}, {@link #PSI_ELEMENT} and other keys and return the array
   * containing unique instances of the found files.
   */
  public static final DataKey<VirtualFile[]> VIRTUAL_FILE_ARRAY = DataKey.create("virtualFileArray");

  /**
   * Returns {@link PsiElement} instance.
   */
  public static final DataKey<PsiElement> PSI_ELEMENT = DataKey.create("psi.Element");

  public static final DataKey<Language> LANGUAGE = DataKey.create("Language");

  /**
   * Returns currently selected {@link PsiFile} instance.
   * <p/>
   * Note that if {@link DataContext} has a value for {@link #VIRTUAL_FILE} key it will automatically return the corresponding {@link PsiFile}
   * for this key if explicit value isn't provided. Also it'll return the containing file if a value for {@link #PSI_ELEMENT} key is provided.
   */
  public static final DataKey<PsiFile> PSI_FILE = DataKey.create("psi.File");

  /**
   * Returns whether the current location relates to a virtual space in an editor.
   *
   * @see com.intellij.openapi.editor.EditorSettings#setVirtualSpace(boolean)
   */
  public static final DataKey<Boolean> EDITOR_VIRTUAL_SPACE = DataKey.create("editor.virtual.space");

  /**
   * Returns a list of target symbols.
   * If there is an editor in the context, then it will return referenced or declared symbols
   * by the current editor file and the caret offset.
   *
   * @see com.intellij.model.psi.impl.TargetSymbolsDataRule
   */
  public static final DataKey<List<Symbol>> SYMBOLS = DataKey.create("symbols");
}
