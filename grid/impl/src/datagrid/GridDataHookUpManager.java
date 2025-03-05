package com.intellij.database.datagrid;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.vfs.fragment.CsvTableDataFragmentFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class GridDataHookUpManager {
  private final Project myProject;
  private final Set<HookUpReference> myHookUps;
  private final Object myLock;

  public GridDataHookUpManager(@NotNull Project project) {
    myProject = project;
    myHookUps = new HashSet<>();
    myLock = new Object();
  }

  public List<GridDataHookUp<GridRow, GridColumn>> getHookUps() {
    synchronized (myLock) {
      return myHookUps.stream()
        .filter(h -> h.myReferenceCount > 0)
        .map(h -> h.myHookUp)
        .collect(Collectors.toList());
    }
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public static GridDataHookUpManager getInstance(@NotNull Project project) {
    return project.getService(GridDataHookUpManager.class);
  }

  public @NotNull CsvDocumentDataHookUp getHookUp(@NotNull CsvTableDataFragmentFile file, @NotNull Disposable parent) {
    return getOrCreateHookUp(file, f -> new CsvDocumentDataHookUp(myProject, f.getFormat(), getDocument(f.getOriginalFile()), f.getRange()), parent);
  }

  public @NotNull CsvDocumentDataHookUp getHookUp(@NotNull VirtualFile file, final @NotNull CsvFormat format, @NotNull Disposable parent) {
    return getOrCreateHookUp(file, file1 -> new CsvDocumentDataHookUp(myProject, format, getDocument(file1), null), parent);
  }

  public static @NotNull Document getDocument(@NotNull VirtualFile file) {
    return Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(file));
  }

  public @Nullable GridDataHookUp<GridRow, GridColumn> acquire(@NotNull GridDataHookUpManager.HookUpHandle handle, @NotNull Disposable parent) {
    synchronized (myLock) {
      if (handle.myRef == null || handle.myRef.myReferenceCount <= 0) return null;
      Disposer.register(parent, createHookUpReferenceDisposable(handle.myRef));
      return handle.myRef.myHookUp;
    }
  }

  public @NotNull GridDataHookUpManager.HookUpHandle getHandle(@NotNull GridDataHookUp<GridRow, GridColumn> hookUp) {
    synchronized (myLock) {
      HookUpReference res = null;
      for (HookUpReference ref : myHookUps) {
        if (ref.myHookUp != hookUp) continue;
        res = ref;
        break;
      }
      return new GridDataHookUpManager.HookUpHandle(res);
    }
  }

  private Disposable createHookUpReferenceDisposable(final @NotNull HookUpReference ref) {
    if (!isClosingToReopen(ref)) {
      ref.myReferenceCount++;
    }
    return new Disposable() {
      private final AtomicBoolean myDisposed = new AtomicBoolean(false);

      @Override
      public void dispose() {
        if (!myDisposed.compareAndSet(false, true)) return;
        if (isClosingToReopen(ref)) return;

        synchronized (myLock) {
          if (--ref.myReferenceCount <= 0) {
            Disposer.dispose(ref);
            myHookUps.remove(ref);
          }
        }
      }
    };
  }

  private static boolean isClosingToReopen(@NotNull HookUpReference ref) {
    VirtualFile file = GridUtil.getVirtualFile(ref.myHookUp);
    return file != null && file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) == Boolean.TRUE;
  }

  public <F extends VirtualFile, H extends GridDataHookUp<GridRow, GridColumn>> H getOrCreateHookUp(final @NotNull F file,
                                                                                                    @NotNull Function<F, H> hookUpFactory,
                                                                                                    @NotNull Disposable parent) {
    synchronized (myLock) {
      H hookUp = hookUpFactory.fun(file);
      HookUpReference ref = new HookUpReference(hookUp);
      myHookUps.add(ref);
      Disposer.register(parent, createHookUpReferenceDisposable(ref));
      //noinspection unchecked
      return (H)ref.myHookUp;
    }
  }

  public <T extends GridDataHookUp<GridRow, GridColumn>> T registerHookUp(T hookUp, @NotNull Disposable parent) {
    synchronized (myLock) {
      HookUpReference ref = new HookUpReference(hookUp);
      myHookUps.add(ref);
      Disposer.register(parent, createHookUpReferenceDisposable(ref));
      return hookUp;
    }
  }

  private static class HookUpReference implements Disposable {
    private final GridDataHookUp<GridRow, GridColumn> myHookUp;
    private int myReferenceCount = 0;

    private HookUpReference(GridDataHookUp<GridRow, GridColumn> hookUp) {
      myHookUp = hookUp;
      if (myHookUp instanceof Disposable) {
        Disposer.register(this, (Disposable)myHookUp);
      }
    }

    @Override
    public void dispose() {
    }
  }

  public static final class HookUpHandle {
    private final HookUpReference myRef;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HookUpHandle handle = (HookUpHandle)o;
      return Objects.equals(myRef, handle.myRef);
    }

    @Override
    public int hashCode() {
      return myRef != null ? myRef.hashCode() : 0;
    }

    private HookUpHandle(@Nullable HookUpReference ref) {
      myRef = ref;
    }
  }
}
