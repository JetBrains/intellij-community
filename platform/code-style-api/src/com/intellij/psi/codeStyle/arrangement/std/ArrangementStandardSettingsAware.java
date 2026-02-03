// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Defines a contract for {@link Rearranger} implementation which wants to use standard platform UI for configuring
 * and managing arrangement settings.
 */
public interface ArrangementStandardSettingsAware {

  /**
   * @return  settings to use by default, i.e. when a user hasn't been explicitly modified arrangement settings;
   *          {@code null} as an indication that no default settings are available
   */
  @Nullable
  StdArrangementSettings getDefaultSettings();

  /**
   * @return    ordered collection of grouping tokens eligible to use with the current rearranger.
   *            <b>Note:</b> platform code which uses this method caches returned results
   */
  @Nullable
  List<CompositeArrangementSettingsToken> getSupportedGroupingTokens();

  /**
   * @return    ordered collection of matching tokens eligible to use with the current rearranger
   *            <b>Note:</b> platform code which uses this method caches returned results
   */
  @Nullable
  List<CompositeArrangementSettingsToken> getSupportedMatchingTokens();

  /**
   * Allows to answer if given token is enabled in combination with other conditions specified by the given condition object.
   * <p/>
   * Example: say, current rearranger is for java and given condition is like 'public class'. This method is expected to
   * return {@code false} for token 'volatile' (because it can be applied only to fields) but {@code true}
   * for token 'abstract' (a java class can be abstract).
   *
   * @param token    target token to check
   * @param current  an object which represents currently chosen tokens; {@code null} if no other token is selected
   * @return         {@code true} if given token is enabled with the given condition; {@code false} otherwise
   */
  boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current);

  /**
   * This method is assumed to be used only by third-party developers. All built-in IJ conditions are supposed
   * to be implemented in terms of {@link StdArrangementTokens}.
   *
   * @param condition  target condition
   * @return           a matcher for the given condition
   * @throws IllegalArgumentException   if current rearranger doesn't know how to build a matcher from the given condition
   */
  @NotNull
  ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException;

  /**
   * @return    collections of mutual exclusion settings. It's is used by standard arrangement settings UI to automatically
   *            deselect elements on selection change. Example: 'private' modifier was selected. When any other modifier is selected
   *            'public' modifier is deselected if returned collection contains set of all supported visibility modifiers
   */
  @NotNull
  @Unmodifiable
  Collection<Set<ArrangementSettingsToken>> getMutexes();

  /**
   * Helps to create links from the 'Actions on Save' page in Settings (Preferences) to the Arrangement tab of the language-specific
   * Code Style page.
   */
  default @NotNull @Unmodifiable Collection<ArrangementTabInfo> getArrangementTabInfos() {
    ExtensionPoint<KeyedLazyInstance<Rearranger<?>>> point = Rearranger.EXTENSION.getPoint();
    if (point == null) return Collections.emptyList();

    KeyedLazyInstance<Rearranger<?>> instance = ContainerUtil.find(point.getExtensionList(), lazyInst -> lazyInst.getInstance() == this);
    // Rearranger.EXTENSION is a LanguageExtension, key is language ID.
    Language language = instance != null ? Language.findLanguageByID(instance.getKey()) : null;
    if (language == null) return Collections.emptyList();

    LanguageFileType fileType = FileTypeRegistry.getInstance().findFileTypeByLanguage(language);
    Icon icon = fileType != null ? fileType.getIcon() : null;

    return List.of(new ArrangementTabInfo(icon, language.getDisplayName(), CodeStyleSettings.generateConfigurableIdByLanguage(language)));
  }

  class ArrangementTabInfo {
    public final @Nullable Icon icon;
    public final @NotNull @NlsSafe String languageDisplayName;
    public final @NotNull @NonNls String configurableId;

    /**
     * This information is used to create links from the 'Actions on Save' page in Settings (Preferences) to the Arrangement tab of the
     * language-specific Code Style page.
     *
     * @param configurableId must be equal to the configurable id of the corresponding configurable, to locate its Code Style page.
     */
    public ArrangementTabInfo(@Nullable Icon icon, @NotNull @NlsSafe String languageDisplayName, @NotNull @NonNls String configurableId) {
      this.icon = icon;
      this.languageDisplayName = languageDisplayName;
      this.configurableId = configurableId;
    }
  }
}
