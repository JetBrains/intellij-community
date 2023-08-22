// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A utility class used to query the language for PSI from {@link LanguageSubstitutor} extensions.
 */
@Service
public final class LanguageSubstitutors extends LanguageExtension<LanguageSubstitutor> {
  /**
   * @deprecated Use {@link #getInstance()}
   */
  @Deprecated
  public static final LanguageSubstitutors INSTANCE = getInstance();

  private static final Logger LOG = Logger.getInstance(LanguageSubstitutors.class);
  private static final Key<Key<String>> PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY = Key.create("PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY");
  private static final AtomicBoolean REQUESTS_DRAIN_NEEDED = new AtomicBoolean(true);
  private static final ConcurrentMap<VirtualFile, SubstitutionInfo> ourReparsingRequests = new ConcurrentHashMap<>();

  public static @NotNull LanguageSubstitutors getInstance() {
    return ApplicationManager.getApplication().getService(LanguageSubstitutors.class);
  }

  private LanguageSubstitutors() {
    super("com.intellij.lang.substitutor");
  }

  /**
   * Queries all applicable language substitutors and returns the substituted language, or {@code lang} argument if
   * no substitutor has returned anything.
   *
   * Prefer {@link com.intellij.lang.LanguageUtil#getLanguageForPsi} for top-level language substitution.
   *
   * @see com.intellij.lang.LanguageUtil#getLanguageForPsi
   */
  public @NotNull Language substituteLanguage(@NotNull Language originalLang, @NotNull VirtualFile file, @NotNull Project project) {
    for (LanguageSubstitutor substitutor : forKey(originalLang)) {
      Language substitutedLang = substitutor.getLanguage(file, project);
      if (substitutedLang != null) {
        if (substitutedLang == Language.ANY) {
          LOG.error("For " + originalLang + " and " + file + ", " + substitutor.getClass().getName() + " returned Language.ANY, which is not allowed");
          continue;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("For " + originalLang + " and " + file + ", " + substitutor.getClass().getName() + " returned '" + substitutedLang + "' of " + substitutedLang.getClass());
        }
        processLanguageSubstitution(file, originalLang, substitutedLang, project);
        return substitutedLang;
      }
    }
    return originalLang;
  }

  private static void processLanguageSubstitution(final @NotNull VirtualFile file,
                                                  @NotNull Language originalLang,
                                                  final @NotNull Language substitutedLang,
                                                  @NotNull Project project) {
    if (file instanceof VirtualFileWindow) {
      // Injected files are created with substituted language, no need to reparse:
      //   com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl#doneInjecting
      return;
    }
    Key<String> projectKey = getOrCreateProjectKey(project);
    Language prevSubstitutedLang = Language.findLanguageByID(projectKey.get(file));
    final Language prevLang = ObjectUtils.notNull(prevSubstitutedLang, originalLang);
    if (!prevLang.is(substitutedLang)) {
      if (file.replace(projectKey, prevSubstitutedLang != null ? prevSubstitutedLang.getID() : null, substitutedLang.getID())) {
        if (prevSubstitutedLang == null) {
          return; // no need to reparse for the first language substitution
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return;
        }
        requestReparsing(file, prevLang, substitutedLang);
      }
    }
  }

  private static @NotNull Key<String> getOrCreateProjectKey(@NotNull Project project) {
    Key<String> key = PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY.get(project);
    if (key == null) {
      synchronized (PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY) {
        key = PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY.get(project);
        if (key == null) {
          key = new Key<>("Substituted lang key for " + project.getName());
          PROJECT_KEY_FOR_SUBSTITUTED_LANG_KEY.set(project, key);
        }
      }
    }
    return key;
  }

  private static void requestReparsing(@NotNull VirtualFile file, @NotNull Language prevLang, @NotNull Language substitutedLang) {
    ourReparsingRequests.put(file, new SubstitutionInfo(prevLang, substitutedLang));
    if (REQUESTS_DRAIN_NEEDED.compareAndSet(true, false)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        REQUESTS_DRAIN_NEEDED.set(true);
        List<Map.Entry<VirtualFile, SubstitutionInfo>> set = new ArrayList<>(ourReparsingRequests.entrySet());
        List<VirtualFile> files = new ArrayList<>(set.size());
        int id = 1;
        for (Map.Entry<VirtualFile, SubstitutionInfo> entry : set) {
          VirtualFile f = entry.getKey();
          SubstitutionInfo info = entry.getValue();
          ourReparsingRequests.remove(f);
          if (f.isValid()) {
            LOG.info("Reparsing " + f.getPath() + " because of language substitution " +
                     info.myPrevLang.getID() + "->" + info.mySubstitutedLang.getID() + ", #" + (id++));
            files.add(f);
          }
        }
        if (files.size() > 0) {
          FileContentUtilCore.reparseFiles(files);
        }
      });
    }
  }

  public static void cancelReparsing(@NotNull VirtualFile file) {
    ourReparsingRequests.remove(file);
  }

  private static class SubstitutionInfo {
    private final Language myPrevLang;
    private final Language mySubstitutedLang;

    SubstitutionInfo(@NotNull Language prevLang, @NotNull Language substitutedLang) {
      myPrevLang = prevLang;
      mySubstitutedLang = substitutedLang;
    }
  }
}
