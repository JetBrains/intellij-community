package com.intellij.ide.tags;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public class TagManagerImpl extends TagManager {

  private final Project myProject;

  public TagManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Collection<Tag> getTags(@NotNull PsiElement element) {
    Bookmark bookmark = getBookmark(myProject, element);
    if (bookmark == null) return Collections.emptySet();
    String desc = bookmark.getDescription();
    if (desc.indexOf('#') == -1 || desc.indexOf(' ') == -1) {
      String text = StringUtil.isNotEmpty(desc) && desc.indexOf(' ') == -1 ?
                    (desc.indexOf('#') == -1 ? "#" + desc : desc) : "#tagged";
      return Collections.singleton(new Tag(text, JBColor.RED, bookmark.getIcon()));
    }
    Matcher matcher = Pattern.compile("#\\w[\\w_\\-#]+").matcher(desc);
    TreeSet<Tag> result = new TreeSet<>(Comparator.comparing(o -> o.text));
    int offset=0;
    while (matcher.find(offset)) {
      String text = desc.substring(matcher.start(), matcher.end());
      result.add(new Tag(text, JBColor.RED, bookmark.getIcon()));
      offset = matcher.end();
    }
    return result;
  }

  @Nullable
  private static Bookmark getBookmark(@NotNull Project project, @NotNull PsiElement element) {
    if (!(element instanceof PsiNameIdentifierOwner) || !element.isValid()) return null;

    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    PsiElement nameIdentifier = virtualFile == null ? null : ((PsiNameIdentifierOwner)element).getNameIdentifier();
    TextRange nameRange = nameIdentifier == null ? null : nameIdentifier.getTextRange();
    Document document = nameRange == null ? null : FileDocumentManager.getInstance().getDocument(virtualFile);

    Collection<Bookmark> bookmarks = document == null ? Collections.emptyList() :
                                     BookmarkManager.getInstance(project).getFileBookmarks(virtualFile);
    for (Bookmark bookmark : bookmarks) {
      int line = bookmark.getLine();
      if (line == -1) continue;
      if (nameRange.intersects(document.getLineStartOffset(line), document.getLineEndOffset(line))) {
        return bookmark;
      }
    }
    return null;
  }
}
