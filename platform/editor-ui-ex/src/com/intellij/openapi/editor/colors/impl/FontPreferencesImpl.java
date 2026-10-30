// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.ModifiableFontPreferences;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Font preferences that can change: a list of font families with their sizes, the subfamilies and the character variants.
 * They also hold the ligatures setting and the line spacing.
 * The editor tries the effective font families in order and uses the first one that can display a character.
 * When none of them can, it uses a fallback font.
 * The real font families are the families from the settings, and usually the effective font families are the same list.
 * <p/>
 * The instance keeps its current snapshot in a {@link FontPreferencesSnapshot}.
 * Each change replaces the snapshot with a compare-and-set, so each method is atomic.
 * A sequence of calls is not atomic.
 * To apply several changes as one change, use {@link #runBatchUpdate}.
 * To read several values of one version, use {@link #getSnapshot()}.
 */
public class FontPreferencesImpl extends ModifiableFontPreferences {
  private static final Logger LOG = Logger.getInstance(FontPreferencesImpl.class);
  private static final AtomicReferenceFieldUpdater<FontPreferencesImpl, FontPreferencesSnapshot> SNAPSHOT_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(FontPreferencesImpl.class, FontPreferencesSnapshot.class, "mySnapshot");

  private volatile @NotNull FontPreferencesSnapshot mySnapshot;
  private volatile @Nullable EventDispatcher<ChangeListener> myEventDispatcher;

  public FontPreferencesImpl() {
    this(FontPreferencesSnapshot.EMPTY);
  }

  private FontPreferencesImpl(@NotNull FontPreferencesSnapshot initial) {
    mySnapshot = initial;
  }

  /**
   * Returns the current preferences as an immutable value.
   * Later changes of these preferences do not change the value, so a reader on any thread sees one consistent version.
   * The method returns the same value until the next change.
   * The snapshot does not call the getters of a subclass.
   * So a subclass that overrides a getter must also override this method.
   */
  @Override
  @ApiStatus.Experimental
  public @NotNull FontPreferences getSnapshot() {
    return mySnapshot;
  }

  @Override
  public boolean hasSize(@NotNull String fontName) {
    return mySnapshot.hasSize(fontName);
  }

  @Override
  public float getLineSpacing() {
    return mySnapshot.getLineSpacing();
  }

  @Override
  public int getSize(@NotNull String fontFamily) {
    return (int)(getSize2D(fontFamily) + 0.5);
  }

  @Override
  public float getSize2D(@NotNull String fontFamily) {
    return mySnapshot.getSize2D(fontFamily);
  }

  @Override
  public @NotNull List<@NlsSafe String> getEffectiveFontFamilies() {
    return mySnapshot.getEffectiveFontFamilies();
  }

  @Override
  public @NotNull List<@NlsSafe String> getRealFontFamilies() {
    return mySnapshot.getRealFontFamilies();
  }

  /**
   * @return first element of the {@link #getEffectiveFontFamilies() registered font families} (if any);
   *         {@link #DEFAULT_FONT_NAME} otherwise
   */
  @Override
  public @NotNull @NlsSafe String getFontFamily() {
    return mySnapshot.getFontFamily();
  }

  @Override
  public boolean useLigatures() {
    return mySnapshot.useLigatures();
  }

  @Override
  public @Nullable String getRegularSubFamily() {
    return mySnapshot.getRegularSubFamily();
  }

  @Override
  public @Nullable String getBoldSubFamily() {
    return mySnapshot.getBoldSubFamily();
  }

  @Override
  public @Unmodifiable @NotNull Set<@NotNull String> getCharacterVariants() {
    return mySnapshot.getCharacterVariants();
  }

  @Override
  public void copyTo(@NotNull FontPreferences preferences) {
    mySnapshot.copyTo(preferences);
  }

  @Override
  public void clear() {
    updateSnapshot(FontPreferencesSnapshot::withoutFontsAndFontSizes);
  }

  @Override
  public void clearFonts() {
    updateSnapshot(FontPreferencesSnapshot::withoutFonts);
  }

  @Override
  public void setLineSpacing(float lineSpacing) {
    updateSnapshot(current -> current.withLineSpacing(lineSpacing));
  }

  @Override
  public void register(@NotNull @NonNls String fontFamily, int size) {
    register(fontFamily, (float)size);
  }

  @Override
  public void register(@NotNull @NonNls String fontFamily, float size) {
    updateSnapshot(current -> current.withRegisteredFontFamily(fontFamily, size));
  }

  @Override
  public void addFontFamily(@NotNull String fontFamily) {
    updateSnapshot(current -> current.withFontFamily(fontFamily));
  }

  @Override
  public void resetFontSizes() {
    updateSnapshot(FontPreferencesSnapshot::withoutFontSizes);
  }

  @Override
  public void setFontSize(@NotNull String fontFamily, int size) {
    setFontSize(fontFamily, (float)size);
  }

  @Override
  public void setFontSize(@NotNull String fontFamily, float size) {
    updateSnapshot(current -> current.withFontSize(fontFamily, size));
  }

  @Override
  public void setTemplateFontSize(int size) {
    setTemplateFontSize((float)size);
  }

  @Override
  public void setTemplateFontSize(float size) {
    updateSnapshot(current -> current.withTemplateFontSize(size));
  }

  @Override
  public void setEffectiveFontFamilies(@NotNull List<String> fontFamilies) {
    updateSnapshot(current -> current.withEffectiveFontFamilies(fontFamilies));
  }

  @Override
  public void setRealFontFamilies(@NotNull List<String> fontFamilies) {
    updateSnapshot(current -> current.withRealFontFamilies(fontFamilies));
  }

  @Override
  public void setUseLigatures(boolean useLigatures) {
    updateSnapshot(current -> current.withUseLigatures(useLigatures));
  }

  @Override
  public void setRegularSubFamily(String subFamily) {
    updateSnapshot(current -> current.withRegularSubFamily(subFamily));
  }

  @Override
  public void setBoldSubFamily(String subFamily) {
    updateSnapshot(current -> current.withBoldSubFamily(subFamily));
  }

  @Override
  public void setCharacterVariants(@Unmodifiable @NotNull Set<@NotNull String> variants) {
    updateSnapshot(current -> current.withCharacterVariants(variants));
  }

  @Override
  public void setCharacterVariant(@NotNull String variant, boolean enabled) {
    updateSnapshot(current -> current.withCharacterVariant(variant, enabled));
  }

  /**
   * Runs the changes on a builder, which creates one snapshot, and writes the snapshot with one compare-and-set.
   * Fires one event if the snapshot changes.
   * A subclass can override a setter, so a subclass instance runs the changes on itself.
   */
  @Override
  @ApiStatus.Experimental
  public void runBatchUpdate(@NotNull Consumer<? super ModifiableFontPreferences> changes) {
    if (getClass() != FontPreferencesImpl.class) {
      changes.accept(this);
      return;
    }
    updateSnapshot(current -> current.withChanges(changes));
  }

  /**
   * Adds a listener that gets one event after each change of the snapshot.
   * The event comes on the thread that made the change.
   */
  public void addChangeListener(@NotNull ChangeListener changeListener) {
    getOrCreateEventDispatcher().addListener(changeListener);
  }

  /**
   * Adds a listener like {@link #addChangeListener(ChangeListener)} and removes it when the disposable is disposed.
   */
  public void addChangeListener(@NotNull ChangeListener changeListener, @NotNull Disposable parentDisposable) {
    getOrCreateEventDispatcher().addListener(changeListener, parentDisposable);
  }

  @Override
  public int hashCode() {
    return mySnapshot.hashCode();
  }

  /**
   * Compares the user settings of the current snapshots.
   * It ignores the effective font families and the template font size.
   * To find any change of the values, compare the results of {@link #getSnapshot()} by identity.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FontPreferencesImpl that = (FontPreferencesImpl)o;
    return mySnapshot.equals(that.mySnapshot);
  }

  @Override
  public @NonNls String toString() {
    return mySnapshot.toString();
  }

  /**
   * Replaces the snapshot with the result of the operation, and fires one event if the snapshot changes.
   * The operation can run more than once, so it must have no side effects.
   * It is the only write of the snapshot. {@link FontPreferencesSnapshot#copyTo} uses it too.
   */
  void updateSnapshot(@NotNull UnaryOperator<FontPreferencesSnapshot> operation) {
    while (true) {
      var prev = mySnapshot;
      var next = operation.apply(prev);
      if (next == prev) {
        return;
      }
      if (SNAPSHOT_UPDATER.compareAndSet(this, prev, next)) {
        logSizeChangeIfNeeded(prev, next);
        notifyStateChanged();
        return;
      }
    }
  }

  private @NotNull EventDispatcher<ChangeListener> getOrCreateEventDispatcher() {
    var dispatcher = myEventDispatcher;
    if (dispatcher != null) {
      return dispatcher;
    }
    synchronized (this) {
      dispatcher = myEventDispatcher;
      if (dispatcher == null) {
        dispatcher = EventDispatcher.create(ChangeListener.class);
        myEventDispatcher = dispatcher;
      }
      return dispatcher;
    }
  }

  private void notifyStateChanged() {
    var dispatcher = myEventDispatcher;
    if (dispatcher == null || !dispatcher.hasListeners()) {
      return;
    }
    dispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }

  private void logSizeChangeIfNeeded(@NotNull FontPreferencesSnapshot prev, @NotNull FontPreferencesSnapshot next) {
    if (!LOG.isDebugEnabled()) {
      return;
    }
    var size = next.getSize2D(next.getFontFamily());
    if (size != prev.getSize2D(prev.getFontFamily())) {
      logSizeChangeIfNeeded(size);
    }
  }

  private void logSizeChangeIfNeeded(float size) {
    if (!LOG.isDebugEnabled()) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return;
    }
    EditorColorsManager colorsManager = application.getServiceIfCreated(EditorColorsManager.class);
    if (colorsManager == null || colorsManager.getGlobalScheme().getFontPreferences() != this) {
      return;
    }
    LOG.debug("Will set size %s to global font (presentationMode=%b)".formatted(size, UISettings.getInstance().getPresentationMode()));
    LOG.debug(ExceptionUtil.currentStackTrace());
  }
}
