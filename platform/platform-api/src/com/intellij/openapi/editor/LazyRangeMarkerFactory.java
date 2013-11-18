/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class LazyRangeMarkerFactory extends AbstractProjectComponent {
  private final ConcurrentMap<VirtualFile,WeakList<LazyMarker>> myMarkers = new ConcurrentWeakHashMap<VirtualFile, WeakList<LazyMarker>>();

  public LazyRangeMarkerFactory(@NotNull Project project, @NotNull final FileDocumentManager fileDocumentManager) {
    super(project);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        VirtualFile docFile = fileDocumentManager.getFile(e.getDocument());
        if (docFile == null) return;
        WeakList<LazyMarker> lazyMarkers = myMarkers.get(docFile);
        if (lazyMarkers == null) return;

        List<LazyMarker> markers = lazyMarkers.toStrongList();
        List<LazyMarker> markersToRemove = new ArrayList<LazyMarker>();
        for (final LazyMarker marker : markers) {
          if (Comparing.equal(marker.getFile(), docFile)) {
            marker.getOrCreateDelegate();
            markersToRemove.add(marker);
          }
        }
        lazyMarkers.removeAll(markersToRemove);
      }
    }, project);
  }

  private void addToLazyMarkersList(@NotNull LazyMarker marker, @NotNull VirtualFile file) {
    List<LazyMarker> markers = myMarkers.get(file);
    if (markers == null) {
      markers = ConcurrencyUtil.cacheOrGet(myMarkers, file, new WeakList<LazyMarker>());
    }
    markers.add(marker);
  }

  public static LazyRangeMarkerFactory getInstance(Project project) {
    return project.getComponent(LazyRangeMarkerFactory.class);
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int offset) {
    return ApplicationManager.getApplication().runReadAction(new Computable<RangeMarker>() {
      @Override
      public RangeMarker compute() {
        // even for already loaded document do not create range marker yet - wait until it really needed when e.g. user clicked to jump to OpenFileDescriptor
        final LazyMarker marker = new OffsetLazyMarker(file, offset);
        addToLazyMarkersList(marker, file);
        return marker;
      }
    });
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int line, final int column, final boolean persistent) {
    return ApplicationManager.getApplication().runReadAction(new Computable<RangeMarker>() {
      @Override
      public RangeMarker compute() {
        FileDocumentManager fdm = FileDocumentManager.getInstance();
        final Document document = fdm.getCachedDocument(file);
        if (document != null) {
          final int offset = calculateOffset(myProject, file, document, line, column);
          return document.createRangeMarker(offset, offset, persistent);
        }

        final LazyMarker marker = new LineColumnLazyMarker(file, line, column);
        addToLazyMarkersList(marker, file);
        return marker;
      }
    });
  }

  private abstract static class LazyMarker extends UserDataHolderBase implements RangeMarker{
    private RangeMarker myDelegate;
    private final VirtualFile myFile;
    protected final int myInitialOffset;

    private LazyMarker(@NotNull VirtualFile file, int offset) {
      myFile = file;
      myInitialOffset = offset;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    private RangeMarker getOrCreateDelegate() {
      if (myDelegate == null) {
        Document document = FileDocumentManager.getInstance().getDocument(myFile);
        if (document == null) {
          return null;
        }
        myDelegate = createDelegate(myFile, document);
      }
      return myDelegate;
    }

    @NotNull
    protected abstract RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull Document document);

    @Override
    @NotNull
    public Document getDocument() {
      return getOrCreateDelegate().getDocument();
    }

    @Override
    public int getStartOffset() {
      return myDelegate == null ? myInitialOffset : myDelegate.getStartOffset();
    }


    @Override
    public int getEndOffset() {
      return myDelegate == null ? myInitialOffset : myDelegate.getEndOffset();
    }

    @Override
    public boolean isValid() {
      RangeMarker delegate = getOrCreateDelegate();
      return delegate != null && delegate.isValid();
    }

    @Override
    public void setGreedyToLeft(boolean greedy) {
      getOrCreateDelegate().setGreedyToLeft(greedy);
    }

    @Override
    public void setGreedyToRight(boolean greedy) {
      getOrCreateDelegate().setGreedyToRight(greedy);
    }

    @Override
    public boolean isGreedyToRight() {
      return getOrCreateDelegate().isGreedyToRight();
    }

    @Override
    public boolean isGreedyToLeft() {
      return getOrCreateDelegate().isGreedyToLeft();
    }

    @Override
    public void dispose() {
      RangeMarker delegate = getOrCreateDelegate();
      if (delegate != null) {
        delegate.dispose();
      }
    }
  }

  private static class OffsetLazyMarker extends LazyMarker {
    private OffsetLazyMarker(@NotNull VirtualFile file, int offset) {
      super(file, offset);
    }

    @Override
    @NotNull
    public RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull final Document document) {
      final int offset = Math.min(myInitialOffset, document.getTextLength());
      return document.createRangeMarker(offset, offset);
    }
  }

  private class LineColumnLazyMarker extends LazyMarker {
    private final int myLine;
    private final int myColumn;

    private LineColumnLazyMarker(@NotNull VirtualFile file, int line, int column) {
      super(file, -1);
      myLine = line;
      myColumn = column;
    }

    @Override
    @NotNull
    public RangeMarker createDelegate(@NotNull VirtualFile file, @NotNull final Document document) {
      int offset = calculateOffset(myProject, file, document, myLine, myColumn);

      return document.createRangeMarker(offset, offset);
    }
  }

  private static int calculateOffset(@NotNull Project project, @NotNull VirtualFile file, @NotNull Document document, final int line, final int column) {
    int offset;
    if (line < document.getLineCount()) {
      final int lineStart = document.getLineStartOffset(line);
      final int lineEnd = document.getLineEndOffset(line);
      final CharSequence docText = document.getCharsSequence();
      final int tabSize = CodeStyleFacade.getInstance(project).getTabSize(file.getFileType());

      offset = lineStart;
      int col = 0;
      while (offset < lineEnd && col < column) {
        col += docText.charAt(offset) == '\t' ? tabSize : 1;
        offset++;
      }
    }
    else {
      offset = document.getTextLength();
    }
    return offset;
  }

}
