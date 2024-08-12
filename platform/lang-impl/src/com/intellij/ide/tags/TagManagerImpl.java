// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.tags;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 *
 * @author gregsh
 */
public final class TagManagerImpl extends TagManager {

  private final Project myProject;

  public TagManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Collection<Tag> getTags(@NotNull PsiElement element) {
    Bookmark bookmark = BookmarkManager.getInstance(myProject).findElementBookmark(element);
    if (bookmark == null) return Collections.emptySet();
    String desc = bookmark.getDescription();
    if (desc.indexOf('#') == -1 || desc.indexOf(' ') == -1) {
      String text = StringUtil.isNotEmpty(desc) && desc.indexOf(' ') == -1 ?
                    (desc.indexOf('#') == -1 ? "#" + desc : desc) : "";
      return Collections.singleton(new Tag(text, JBColor.RED, bookmark.getIcon()));
    }
    Matcher matcher = Pattern.compile("#\\w[\\w_\\-#]*").matcher(desc);
    TreeSet<Tag> result = new TreeSet<>(Comparator.comparing(o -> o.text));
    int offset=0;
    while (matcher.find(offset)) {
      String text = desc.substring(matcher.start(), matcher.end());
      result.add(new Tag(text, JBColor.RED, bookmark.getIcon()));
      offset = matcher.end();
    }
    return result;
  }

}
