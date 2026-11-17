package com.intellij.database.datagrid;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GridDataHookUpManager implements GridHookUpRegistry, Disposable {
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
    return (GridDataHookUpManager)GridHookUpRegistry.getInstance(project);
  }

  public @Nullable GridDataHookUp<GridRow, GridColumn> acquire(@NotNull GridDataHookUpManager.HookUpHandle handle, @NotNull Disposable parent) {
    synchronized (myLock) {
      HookUpReference ref = handle.myRef;
      if (ref == null || ref.myReferenceCount <= 0) return null;
      addReference(ref, parent);
      return ref.myHookUp;
    }
  }

  /**
   * Returns a new owner of the hookup of {@code handle}, or null if the hookup was disposed.
   * Unlike {@link #acquire(HookUpHandle, Disposable)}, it also gets a hookup that an editor kept when it closed to reopen.
   */
  public @Nullable EditorOwner<GridDataHookUp<GridRow, GridColumn>> acquireForEditor(@NotNull GridDataHookUpManager.HookUpHandle handle) {
    synchronized (myLock) {
      HookUpReference ref = handle.myRef;
      if (ref == null || ref.myReferenceCount <= 0 && !ref.myParked) return null;
      return newOwner(ref, ref.myHookUp);
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

  /** Call under {@link #myLock}. */
  private void addReference(@NotNull HookUpReference ref, @NotNull Disposable parent) {
    ref.myReferenceCount++;
    AtomicBoolean released = new AtomicBoolean(false);
    Disposer.register(parent, () -> {
      if (released.compareAndSet(false, true)) releaseReference(ref, false);
    });
  }

  /** Call under {@link #myLock}. */
  private <H extends GridDataHookUp<GridRow, GridColumn>> @NotNull EditorOwner<H> newOwner(@NotNull HookUpReference ref,
                                                                                          @NotNull H hookUp) {
    ref.myParked = false;
    ref.myReferenceCount++;
    return new EditorOwnerImpl<>(ref, hookUp);
  }

  private void releaseReference(@NotNull HookUpReference ref, boolean keepForReopen) {
    synchronized (myLock) {
      if (--ref.myReferenceCount > 0) return;
      if (keepForReopen) {
        ref.myParked = true;
      }
      else {
        Disposer.dispose(ref);
        myHookUps.remove(ref);
      }
    }
  }

  /** Disposes the hookups kept for a reopen. An editor that is still open, such as one in a detached window, releases its own later. */
  @Override
  public void dispose() {
    List<HookUpReference> parked;
    synchronized (myLock) {
      parked = ContainerUtil.filter(myHookUps, ref -> ref.myParked);
      parked.forEach(myHookUps::remove);
    }
    parked.forEach(Disposer::dispose);
  }

  /**
   * Returns a new owner of the hookup of {@code hookUpClass} that an editor of {@code file} kept to reopen.
   * Without one, it registers a hookup from {@code hookUpFactory}.
   */
  public <F extends VirtualFile, H extends GridDataHookUp<GridRow, GridColumn>> @NotNull EditorOwner<H> acquireForEditor(
    final @NotNull F file,
    @NotNull Class<H> hookUpClass,
    @NotNull Function<F, H> hookUpFactory
  ) {
    synchronized (myLock) {
      HookUpReference ref = ContainerUtil.find(myHookUps, r -> r.myParked && r.myReuseClass == hookUpClass &&
                                                               file.equals(GridUtil.getVirtualFile(r.myHookUp)));
      if (ref == null) {
        ref = new HookUpReference(hookUpFactory.apply(file), hookUpClass);
        myHookUps.add(ref);
      }
      return newOwner(ref, hookUpClass.cast(ref.myHookUp));
    }
  }

  @Override
  public <T extends GridDataHookUp<GridRow, GridColumn>> @NotNull T registerHookUp(@NotNull T hookUp, @NotNull Disposable parent) {
    synchronized (myLock) {
      HookUpReference ref = new HookUpReference(hookUp, null);
      myHookUps.add(ref);
      addReference(ref, parent);
      return hookUp;
    }
  }

  private final class EditorOwnerImpl<H extends GridDataHookUp<GridRow, GridColumn>> implements EditorOwner<H> {
    private final @NotNull H myHookUp;
    /** Guarded by {@link #myLock}; null once released. */
    private @Nullable HookUpReference myReference;

    private EditorOwnerImpl(@NotNull HookUpReference reference, @NotNull H hookUp) {
      myReference = reference;
      myHookUp = hookUp;
    }

    @Override
    public @NotNull H getHookUp() {
      return myHookUp;
    }

    @Override
    public void release(boolean keepForReopen) {
      HookUpReference held;
      synchronized (myLock) {
        held = myReference;
        myReference = null;
      }
      if (held != null) releaseReference(held, keepForReopen);
    }
  }

  private static class HookUpReference implements Disposable {
    private final GridDataHookUp<GridRow, GridColumn> myHookUp;
    private final @Nullable Class<?> myReuseClass;
    private int myReferenceCount = 0;
    private boolean myParked;

    private HookUpReference(GridDataHookUp<GridRow, GridColumn> hookUp, @Nullable Class<?> reuseClass) {
      myHookUp = hookUp;
      myReuseClass = reuseClass;
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
