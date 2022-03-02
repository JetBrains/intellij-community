// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.Pair;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.*;

/**
 * @author: db
 */
public abstract class Difference {

  public static boolean weakerAccess(final int me, final int than) {
    return (isPrivate(me) && !isPrivate(than)) || (isProtected(me) && isPublic(than)) || (isPackageLocal(me) && (than & (Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) != 0);
  }

  public static boolean isPrivate(int access) {
    return (access & Opcodes.ACC_PRIVATE) != 0;
  }

  public static boolean isPublic(int access) {
    return (access & Opcodes.ACC_PUBLIC) != 0;
  }

  public static boolean isProtected(int access) {
    return (access & Opcodes.ACC_PROTECTED) != 0;
  }

  public static boolean isPackageLocal(final int access) {
    return (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) == 0;
  }

  public static final int NONE = 0;
  public static final int ACCESS = 1;
  public static final int TYPE = 2;
  public static final int VALUE = 4;
  public static final int SIGNATURE = 8;
  public static final int SUPERCLASS = 16;
  public static final int USAGES = 32;
  public static final int ANNOTATIONS = 64;
  public static final int CONSTANT_REFERENCES = 128;

  public interface Specifier<T, D extends Difference> {
    Collection<T> added();

    Collection<T> removed();

    Collection<Pair<T, D>> changed();

    boolean unchanged();
  }

  public static <T, D extends Difference> Specifier<T, D> make(final Set<T> past, final Set<T> now) {
    if ((past == null || past.isEmpty()) && (now == null || now.isEmpty())) {
      return new Specifier<T, D>() {
        @Override
        public Collection<T> added() {
          return Collections.emptySet();
        }

        @Override
        public Collection<T> removed() {
          return Collections.emptySet();
        }

        @Override
        public Collection<Pair<T, D>> changed() {
          return Collections.emptySet();
        }

        @Override
        public boolean unchanged() {
          return true;
        }
      };
    }

    if (past == null) {
      final Collection<T> _now = Collections.unmodifiableCollection(now);
      return new Specifier<T, D>() {
        @Override
        public Collection<T> added() {
          return _now;
        }

        @Override
        public Collection<T> removed() {
          return Collections.emptyList();
        }

        @Override
        public Collection<Pair<T, D>> changed() {
          return Collections.emptyList();
        }

        @Override
        public boolean unchanged() {
          return false;
        }
      };
    }

    final Set<T> added = new HashSet<>(now);
    added.removeAll(past);

    final Set<T> removed = new HashSet<>(past);
    removed.removeAll(now);

    final Set<Pair<T, D>> changed;
    if (canContainChangedElements(past, now)) {
      changed = new HashSet<>();
      final Set<T> intersect = new HashSet<>(past);
      final Map<T, T> nowMap = new HashMap<>();

      for (T s : now) {
        if (intersect.contains(s)) {
          nowMap.put(s, s);
        }
      }

      intersect.retainAll(now);

      for (T x : intersect) {
        final Proto px = (Proto)x;
        final Proto py = (Proto)nowMap.get(x);
        //noinspection unchecked
        final D diff = (D)py.difference(px);

        if (!diff.no()) {
          changed.add(Pair.create(x, diff));
        }
      }
    }
    else {
      changed = Collections.emptySet();
    }

    return new Specifier<T, D>() {
      @Override
      public Collection<T> added() {
        return added;
      }

      @Override
      public Collection<T> removed() {
        return removed;
      }

      @Override
      public Collection<Pair<T, D>> changed() {
        return changed;
      }

      @Override
      public boolean unchanged() {
        return changed.isEmpty() && added.isEmpty() && removed.isEmpty();
      }
    };
  }

  private static <T> boolean canContainChangedElements(final Collection<T> past, final Collection<T> now) {
    if (past != null && now != null && !past.isEmpty() && !now.isEmpty()) {
      return past.iterator().next() instanceof Proto;
    }
    return false;
  }

  public abstract int base();

  public abstract boolean no();

  public abstract boolean accessRestricted();

  public abstract boolean accessExpanded();

  public abstract int addedModifiers();

  public abstract int removedModifiers();

  public abstract boolean packageLocalOn();

  public abstract boolean hadValue();

  public abstract Specifier<TypeRepr.ClassType, Difference> annotations();
}
