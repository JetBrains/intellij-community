/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.ide.script;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

class Jsr223IdeScriptEngineManagerImpl extends IdeScriptEngineManager {
  private static final Logger LOG = Logger.getInstance(IdeScriptEngineManager.class);

  private final Future<ScriptEngineManager> myManagerFuture = PooledThreadExecutor.INSTANCE.submit(new Callable<ScriptEngineManager>() {
    @Override
    public ScriptEngineManager call() {
      long start = System.currentTimeMillis();
      try {
        return ClassLoaderUtil.runWithClassLoader(AllPluginsLoader.INSTANCE, new Computable<ScriptEngineManager>() {
          @Override
          public ScriptEngineManager compute() {
            return new ScriptEngineManager();
          }
         });
      }
      finally {
        long end = System.currentTimeMillis();
        LOG.info(ScriptEngineManager.class.getName() + " initialized in " + (end - start) + " ms");
      }
    }
  });

  @NotNull
  @Override
  public List<String> getLanguages() {
    return ContainerUtil.map(getScriptEngineManager().getEngineFactories(), new Function<ScriptEngineFactory, String>() {
      @Override
      public String fun(ScriptEngineFactory factory) {
        return factory.getLanguageName();
      }
    });
  }

  @NotNull
  @Override
  public List<String> getFileExtensions(@Nullable String language) {
    List<String> extensions = ContainerUtil.newArrayList();
    List<ScriptEngineFactory> factories = getScriptEngineManager().getEngineFactories();
    for (ScriptEngineFactory factory : factories) {
      if (language == null || factory.getLanguageName().equals(language)) {
        extensions.addAll(factory.getExtensions());
      }
    }
    return extensions;
  }

  @Nullable
  @Override
  public IdeScriptEngine getEngineForLanguage(@NotNull final String language, @Nullable ClassLoader loader) {
    ClassLoader l = ObjectUtils.notNull(loader, AllPluginsLoader.INSTANCE);
    return ClassLoaderUtil.runWithClassLoader(l, new Computable<IdeScriptEngine>() {
      @Override
      public IdeScriptEngine compute() {
        return createIdeScriptEngine(getScriptEngineManager().getEngineByName(language));
      }
    });
  }

  @Nullable
  @Override
  public IdeScriptEngine getEngineForFileExtension(@NotNull final String extension, @Nullable ClassLoader loader) {
    ClassLoader l = ObjectUtils.notNull(loader, AllPluginsLoader.INSTANCE);
    return ClassLoaderUtil.runWithClassLoader(l, new Computable<IdeScriptEngine>() {
      @Override
      public IdeScriptEngine compute() {
        return createIdeScriptEngine(getScriptEngineManager().getEngineByExtension(extension));
      }
    });
  }

  @Override
  public boolean isInitialized() {
    return myManagerFuture.isDone();
  }

  @NotNull
  private ScriptEngineManager getScriptEngineManager() {
    ScriptEngineManager manager = null;
    try {
      manager = myManagerFuture.get();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return ObjectUtils.assertNotNull(manager);
  }

  @Nullable
  private static IdeScriptEngine createIdeScriptEngine(@Nullable ScriptEngine engine) {
    return engine == null ? null : redirectOutputToLog(new Jsr223IdeScriptEngine(engine));
  }

  private static IdeScriptEngine redirectOutputToLog(IdeScriptEngine engine) {
    class Log extends Writer {
      final boolean error;
      Log(boolean error) {this.error = error;}
      @Override public void flush() throws IOException { }
      @Override public void close() throws IOException { }
      @Override public void write(char[] cbuf, int off, int len) throws IOException {
        while (len > 0 && Character.isWhitespace(cbuf[off + len - 1])) len --;
        if (len == 0) return;
        String s = new String(cbuf, off, len);
        if (error) LOG.warn(s); else LOG.info(s);
      }
    }
    engine.setStdOut(new Log(false));
    engine.setStdErr(new Log(true));
    return engine;
  }

  static class Jsr223IdeScriptEngine implements IdeScriptEngine {
    private final ScriptEngine myEngine;
    private final ClassLoader myLoader;

    Jsr223IdeScriptEngine(ScriptEngine engine) {
      myEngine = engine;
      myLoader = Thread.currentThread().getContextClassLoader();
    }

    @Override
    public Object getBinding(@NotNull String name) {
      return myEngine.get(name);
    }

    @Override
    public void setBinding(@NotNull String name, Object value) {
      myEngine.put(name, value);
    }

    @NotNull
    @Override
    public Writer getStdOut() {
      return myEngine.getContext().getWriter();
    }

    @Override
    public void setStdOut(@NotNull Writer writer) {
      myEngine.getContext().setWriter(writer);
    }

    @NotNull
    @Override
    public Writer getStdErr() {
      return myEngine.getContext().getErrorWriter();
    }

    @Override
    public void setStdErr(@NotNull Writer writer) {
      myEngine.getContext().setErrorWriter(writer);
    }

    @NotNull
    @Override
    public Reader getStdIn() {
      return myEngine.getContext().getReader();
    }

    @Override
    public void setStdIn(@NotNull Reader reader) {
      myEngine.getContext().setReader(reader);
    }

    @NotNull
    @Override
    public String getLanguage() {
      return myEngine.getFactory().getLanguageName();
    }

    @NotNull
    @Override
    public List<String> getFileExtensions() {
      return myEngine.getFactory().getExtensions();
    }

    @Override
    public Object eval(@NotNull final String script) throws IdeScriptException {
      return ClassLoaderUtil.runWithClassLoader(myLoader, new ThrowableComputable<Object, IdeScriptException>() {
        @Override
        public Object compute() throws IdeScriptException {
          try {
            return myEngine.eval(script);
          }
          catch (Throwable ex) {
            //noinspection InstanceofCatchParameter
            while (ex instanceof ScriptException && ex.getCause() != null) ex = ex.getCause();
            throw new IdeScriptException(ex);
          }
        }
      });
    }
  }

  static class AllPluginsLoader extends ClassLoader {
    static final AllPluginsLoader INSTANCE = new AllPluginsLoader();

    final ConcurrentMap<Long, ClassLoader> myLuckyGuess = ContainerUtil.newConcurrentMap();

    public AllPluginsLoader() {
      // Groovy performance: do not specify parent loader to enable our luckyGuesser
      // Also specify null explicitly to suppress getSystemClassLoader() as parent
      super(null);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      //long ts = System.currentTimeMillis();

      int p0 = name.indexOf("$");
      boolean hasBase = p0 > 0;
      int p1 = hasBase ? name.indexOf("$", p0 + 1) : -1;
      String base = hasBase ? name.substring(0, Math.max(p0, p1)) : name;
      long hash = StringHash.calc(base);

      Class<?> c = null;
      ClassLoader guess1 = myLuckyGuess.get(hash);   // cached loader
      ClassLoader guess2 = myLuckyGuess.get(0L);     // last recently used
      for (ClassLoader loader : JBIterable.of(guess1, guess2)) {
        if (loader == this) throw new ClassNotFoundException(name);
        if (loader == null) continue;
        try {
          c = loader.loadClass(name);
          break;
        }
        catch (ClassNotFoundException ignored) {
        }
      }
      if (c == null) {
        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          ClassLoader l = descriptor.getPluginClassLoader();
          if (l == null || l == guess1 || l == guess2) continue;
          try {
            if (hasBase) {
              l.loadClass(base);
              myLuckyGuess.putIfAbsent(hash, l);
            }
            try {
              c = l.loadClass(name);
              myLuckyGuess.putIfAbsent(hash, l);
              myLuckyGuess.put(0L, l);
              break;
            }
            catch (ClassNotFoundException e) {
              if (hasBase) break;
              if (name.startsWith("java.") || name.startsWith("groovy.")) break;
            }
          }
          catch (ClassNotFoundException ignored) {
          }
        }
      }

      //LOG.info("AllPluginsLoader [" + StringUtil.formatDuration(System.currentTimeMillis() - ts) + "]: " + (c != null ? "+" : "-") + name);
      if (c != null) {
        return c;
      }
      else {
        myLuckyGuess.putIfAbsent(hash, this);
        throw new ClassNotFoundException(name);
      }
    }

    private static boolean isAllowedPluginResource(String name) {
      // allow plugin engines but suppress all other resources
      return "META-INF/services/javax.script.ScriptEngineFactory".equals(name);
    }

    @Override
    protected URL findResource(String name) {
      if (isAllowedPluginResource(name)) {
        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          ClassLoader l = descriptor.getPluginClassLoader();
          URL url = l == null ? null : l.getResource(name);
          if (url != null) return url;
        }
      }
      return getClass().getClassLoader().getResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
      if (isAllowedPluginResource(name)) {
        Set<URL> result = null;
        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          ClassLoader l = descriptor.getPluginClassLoader();
          Enumeration<URL> urls = l == null ? null : l.getResources(name);
          if (urls == null || !urls.hasMoreElements()) continue;
          if (result == null) result = ContainerUtil.newLinkedHashSet();
          ContainerUtil.addAll(result, urls);
        }
        if (result != null) {
          return Collections.enumeration(result);
        }
      }
      return getClass().getClassLoader().getResources(name);
    }
  }
}
