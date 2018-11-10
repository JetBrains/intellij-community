// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.tools.Diagnostic;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: 01-Oct-18
 */
public abstract class JpsJavacFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> implements StandardJavaFileManager {
  protected final Context myContext;
  protected Map<File, Set<File>> myOutputsMap = Collections.emptyMap();

  public JpsJavacFileManager(final Context context) {
    super(context.getStandardFileManager());
    myContext = new Context() {
      @Override
      public boolean isCanceled() {
        return context.isCanceled();
      }

      @NotNull
      @Override
      public StandardJavaFileManager getStandardFileManager() {
        return context.getStandardFileManager();
      }

      @Override
      public void consumeOutputFile(@NotNull OutputFileObject obj) {
        try {
          context.consumeOutputFile(obj);
        }
        finally {
          onOutputFileGenerated(obj.getFile());
        }
      }

      @Override
      public void reportMessage(Diagnostic.Kind kind, String message) {
        context.reportMessage(kind, message);
      }
    };
  }

  interface Context {
    boolean isCanceled();

    @NotNull
    StandardJavaFileManager getStandardFileManager();

    void consumeOutputFile(@NotNull OutputFileObject obj);

    void reportMessage(final Diagnostic.Kind kind, String message);
  }

  public final Context getContext() {
    return myContext;
  }

  @NotNull
  protected StandardJavaFileManager getStdManager() {
    return fileManager;
  }

  public void onOutputFileGenerated(File file) {
  };

  @Override
  public void close() {
    try {
      super.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      myOutputsMap = Collections.emptyMap();
    }
  }

  public void setOutputDirectories(final Map<File, Set<File>> outputDirToSrcRoots) throws IOException {
    for (File outputDir : outputDirToSrcRoots.keySet()) {
      // this will validate output dirs
      setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDir));
    }
    myOutputsMap = outputDirToSrcRoots;
  }

  public static <T> Iterable<T> merge(final Iterable<T> first, final Iterable<T> second) {
    return new Iterable<T>() {
      @Override
      @NotNull
      public Iterator<T> iterator() {
        final Iterator<T> i1 = first.iterator();
        final Iterator<T> i2 = second.iterator();
        return new Iterator<T>() {
          @Override
          public boolean hasNext() {
            return i1.hasNext() || i2.hasNext();
          }

          @Override
          public T next() {
            return i1.hasNext()? i1.next() : i2.next();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public static <T> Iterable<T> merge(final Collection<Iterable<T>> parts) {
    if (parts.isEmpty()) {
      return Collections.emptyList();
    }
    if (parts.size() == 1) {
      return parts.iterator().next();
    }
    return merge((Iterable<Iterable<T>>)parts);
  }

  public static <T> Iterable<T> merge(final Iterable<Iterable<T>> parts) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        final Iterator<Iterable<T>> partsIterator = parts.iterator();
        return new Iterator<T>() {
          Iterator<T> currentPart;
          @Override
          public boolean hasNext() {
            return getCurrentPart() != null;
          }

          @Override
          public T next() {
            final Iterator<T> part = getCurrentPart();
            if (part != null) {
              return part.next();
            }
            throw new NoSuchElementException();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          private Iterator<T> getCurrentPart() {
            while (currentPart == null || !currentPart.hasNext()) {
              if (partsIterator.hasNext()) {
                currentPart = partsIterator.next().iterator();
              }
              else {
                currentPart = null;
                break;
              }
            }
            return currentPart;
          }
        };
      }
    };
  }

  public static <I,O> Iterable<O> convert(final Iterable<? extends I> from, final Function<I, ? extends O> converter) {
    return new Iterable<O>() {
      @NotNull
      @Override
      public Iterator<O> iterator() {
        final Iterator<? extends I> it = from.iterator();
        return new Iterator<O>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public O next() {
            return converter.fun(it.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public static <T> Iterable<T> filter(final Iterable<T> data, final BooleanFunction<? super T> acceptElement) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        final Iterator<T> it = data.iterator();
        return new Iterator<T>() {
          private T current = null;
          private boolean isPending = false;

          @Override
          public boolean hasNext() {
            if (!isPending) {
              findNext();
            }
            return isPending;
          }

          @Override
          public T next() {
            try {
              if (!isPending) {
                findNext();
                if (!isPending) {
                  throw new NoSuchElementException();
                }
              }
              return current;
            }
            finally {
              current = null;
              isPending = false;
            }
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          private void findNext() {
            isPending = false;
            current = null;
            while (it.hasNext()) {
              final T next = it.next();
              if (acceptElement.fun(next)) {
                isPending = true;

                current = next;
                break;
              }
            }
          }
        };
      }
    };
  }
}
