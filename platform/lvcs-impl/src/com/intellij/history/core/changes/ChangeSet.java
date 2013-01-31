/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core.changes;

import com.intellij.history.core.Content;
import com.intellij.history.core.StreamUtil;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.util.Producer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeSet {
  private final long myId;
  @Nullable private String myName;
  private final long myTimestamp;
  private final List<Change> myChanges;

  private volatile boolean isLocked = false;

  public ChangeSet(long id, long timestamp) {
    myId = id;
    myTimestamp = timestamp;
    myChanges = ContainerUtil.createLockFreeCopyOnWriteList();
  }

  public ChangeSet(DataInput in) throws IOException {
    myId = in.readLong();
    myName = StreamUtil.readStringOrNull(in);
    myTimestamp = in.readLong();

    int count = in.readInt();
    List<Change> changes = new ArrayList<Change>(count);
    while (count-- > 0) {
      changes.add(StreamUtil.readChange(in));
    }
    myChanges = Collections.unmodifiableList(changes);
    isLocked = true;
  }

  public void write(DataOutput out) throws IOException {
    out.writeLong(myId);
    StreamUtil.writeStringOrNull(out, myName);
    out.writeLong(myTimestamp);

    out.writeInt(myChanges.size());
    for (Change c : myChanges) {
      StreamUtil.writeChange(out, c);
    }
  }

  public void setName(@Nullable String name) {
    myName = name;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public void lock() {
    isLocked = true;
  }

  @Nullable
  public String getLabel() {
    return accessChanges(new Producer<String>() {
      @Override
      public String produce() {
        for (Change each : myChanges) {
          if (each instanceof PutLabelChange) {
            return ((PutLabelChange)each).getName();
          }
        }
        return null;
      }
    });
  }

  public int getLabelColor() {
    return accessChanges(new Producer<Integer>() {
      @Override
      public Integer produce() {
        for (Change each : myChanges) {
          if (each instanceof PutSystemLabelChange) {
            return ((PutSystemLabelChange)each).getColor();
          }
        }
        return -1;
      }
    });
  }

  public void addChange(final Change c) {
    LocalHistoryLog.LOG.assertTrue(!isLocked, "Changset is already locked");
    accessChanges(new Runnable() {
      @Override
      public void run() {
        myChanges.add(c);
      }
    });
  }

  public List<Change> getChanges() {
    return accessChanges(new Producer<List<Change>>() {
      @Override
      public List<Change> produce() {
        if (isLocked) return myChanges;
        return Collections.unmodifiableList(new ArrayList<Change>(myChanges));
      }
    });
  }

  public boolean isEmpty() {
    return accessChanges(new Producer<Boolean>() {
      @Override
      public Boolean produce() {
        return myChanges.isEmpty();
      }
    });
  }

  public boolean affectsPath(final String paths) {
    return accessChanges(new Producer<Boolean>() {
      @Override
      public Boolean produce() {
        for (Change c : myChanges) {
          if (c.affectsPath(paths)) return true;
        }
        return false;
      }
    });
  }

  public boolean isCreationalFor(final String path) {
    return accessChanges(new Producer<Boolean>() {
      @Override
      public Boolean produce() {
        for (Change c : myChanges) {
          if (c.isCreationalFor(path)) return true;
        }
        return false;
      }
    });
  }

  public List<Content> getContentsToPurge() {
    return accessChanges(new Producer<List<Content>>() {
      @Override
      public List<Content> produce() {
        List<Content> result = new ArrayList<Content>();
        for (Change c : myChanges) {
          result.addAll(c.getContentsToPurge());
        }
        return result;
      }
    });
  }

  public boolean isContentChangeOnly() {
    return accessChanges(new Producer<Boolean>() {
      @Override
      public Boolean produce() {
        return myChanges.size() == 1 && getFirstChange() instanceof ContentChange;
      }
    });
  }

  public boolean isLabelOnly() {
    return accessChanges(new Producer<Boolean>() {
      @Override
      public Boolean produce() {
        return myChanges.size() == 1 && getFirstChange() instanceof PutLabelChange;
      }
    });
  }

  public Change getFirstChange() {
    return accessChanges(new Producer<Change>() {
      @Override
      public Change produce() {
        return myChanges.get(0);
      }
    });
  }

  public Change getLastChange() {
    return accessChanges(new Producer<Change>() {
      @Override
      public Change produce() {
        return myChanges.get(myChanges.size() - 1);
      }
    });
  }

  public List<String> getAffectedPaths() {
    return accessChanges(new Producer<List<String>>() {
      @Override
      public List<String> produce() {
        List<String> result = new SmartList<String>();
        for (Change each : myChanges) {
          if (each instanceof StructuralChange) {
            result.add(((StructuralChange)each).getPath());
          }
        }
        return result;
      }
    });
  }

  public String toString() {
    return accessChanges(new Producer<String>() {
      @Override
      public String produce() {
        return myChanges.toString();
      }
    });
  }

  public long getId() {
    return myId;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChangeSet change = (ChangeSet)o;

    if (myId != change.myId) return false;

    return true;
  }

  @Override
  public final int hashCode() {
    return (int)(myId ^ (myId >>> 32));
  }

  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    if (isLocked) {
      doAccept(v);
      return;
    }

    synchronized (myChanges) {
      doAccept(v);
    }
  }

  private void doAccept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.begin(this);
    for (Change c : ContainerUtil.iterateBackward(myChanges)) {
      c.accept(v);
    }
    v.end(this);
  }

  private <T> T accessChanges(@NotNull Producer<T> func) {
    if (isLocked) {
      //noinspection ConstantConditions
      return func.produce();
    }

    synchronized (myChanges) {
      //noinspection ConstantConditions
      return func.produce();
    }
  }

  private void accessChanges(@NotNull final Runnable func) {
    accessChanges(new Producer<Object>() {
      @Override
      public Object produce() {
        func.run();
        return null;
      }
    });
  }
}
