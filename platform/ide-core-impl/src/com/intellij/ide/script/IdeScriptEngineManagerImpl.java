// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.script;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class IdeScriptEngineManagerImpl extends IdeScriptEngineManager {
  private static final Logger LOG = Logger.getInstance(IdeScriptEngineManagerImpl.class);

  private final SynchronizedClearableLazy<Map<EngineInfo, ScriptEngineFactory>> myFactories = new SynchronizedClearableLazy<>(() -> {
    long start = System.nanoTime();
    Map<EngineInfo, ScriptEngineFactory> map = calcFactories();
    LOG.info(TimeoutUtil.getDurationMillis(start) + " ms to enumerate javax.scripting engines on " +
             (EDT.isCurrentThreadEdt() ? "EDT" : "BGT"));
    return map;
  });

  IdeScriptEngineManagerImpl() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        dropFactories();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      }

      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        dropFactories();
      }
    });
  }

  @Override
  public @NotNull List<EngineInfo> getEngineInfos() {
    return new ArrayList<>(getFactories().keySet());
  }

  @Override
  public @Nullable IdeScriptEngine getEngine(@NotNull EngineInfo engineInfo, @Nullable ClassLoader loader) {
    ScriptEngineFactory engineFactory = getFactories().get(engineInfo);
    if (engineFactory == null) return null;
    return createIdeScriptEngine(engineFactory, loader);
  }

  @Override
  public @Nullable IdeScriptEngine getEngineByName(@NotNull String engineName, @Nullable ClassLoader loader) {
    Map<EngineInfo, ScriptEngineFactory> state = getFactories();
    for (EngineInfo info : state.keySet()) {
      if (!info.engineName.equals(engineName)) continue;
      return createIdeScriptEngine(state.get(info), loader);
    }
    return null;
  }

  @Override
  public @Nullable IdeScriptEngine getEngineByFileExtension(@NotNull String extension, @Nullable ClassLoader loader) {
    Map<EngineInfo, ScriptEngineFactory> state = getFactories();
    for (EngineInfo info : state.keySet()) {
      if (!info.fileExtensions.contains(extension)) continue;
      return createIdeScriptEngine(state.get(info), loader);
    }
    return null;
  }


  private @NotNull Map<EngineInfo, ScriptEngineFactory> getFactories() {
    try {
      return myFactories.getValue();
    }
    catch (Exception e) {
      LOG.error(e);
      return Collections.emptyMap();
    }
  }

  private void dropFactories() {
    myFactories.drop();
  }

  private static @NotNull Map<EngineInfo, ScriptEngineFactory> calcFactories() {
    // bundled factories from java modules (Groovy) and from plugins
    return JBIterable.of(PluginManagerCore.getPlugins())
      .flatten(o -> {
        try {
          return new ScriptEngineManager(o.getClassLoader()).getEngineFactories();
        }
        catch (Throwable e) {
          LOG.error(new PluginException(e, o.getPluginId()));
          return null;
        }
      })
      .unique(o -> o.getClass().getName())
      .filterMap(factory -> {
        try {
          Class<? extends ScriptEngineFactory> aClass = factory.getClass();
          ClassLoader classLoader = aClass.getClassLoader();
          PluginDescriptor plugin = classLoader instanceof PluginAwareClassLoader
                                    ? ((PluginAwareClassLoader)classLoader).getPluginDescriptor() : null;
          EngineInfo info = new EngineInfo(
            factory.getEngineName(),
            factory.getEngineVersion(),
            factory.getLanguageName(),
            factory.getLanguageVersion(),
            factory.getExtensions(),
            aClass.getName(),
            plugin);
          return Pair.create(info, factory);
        }
        catch (Throwable e) {
          LOG.error(PluginException.createByClass(e, factory.getClass()));
          return null;
        }
      })
      .toMap(o -> o.first, o -> o.second);
  }

  private static @Nullable IdeScriptEngine createIdeScriptEngine(@Nullable ScriptEngineFactory scriptEngineFactory,
                                                                 @Nullable ClassLoader loader) {
    if (scriptEngineFactory == null) {
      return null;
    }
    IdeScriptEngine engine = new EngineImpl(scriptEngineFactory, loader == null ? AllPluginsLoader.INSTANCE : loader);
    redirectOutputToLog(engine);

    IdeUiService.getInstance().logIdeScriptUsageEvent(scriptEngineFactory.getClass());

    return engine;
  }

  private static void redirectOutputToLog(@NotNull IdeScriptEngine engine) {
    final class Log extends Writer {
      private final boolean error;

      private Log(boolean error) {
        this.error = error;
      }

      @Override
      public void flush() { }

      @Override
      public void close() { }

      @Override
      public void write(char[] cbuf, int off, int len) {
        while (len > 0 && Character.isWhitespace(cbuf[off + len - 1])) len--;
        if (len == 0) return;
        String s = new String(cbuf, off, len);
        if (error) {
          LOG.warn(s);
        }
        else {
          LOG.info(s);
        }
      }
    }
    engine.setStdOut(new Log(false));
    engine.setStdErr(new Log(true));
  }

  static final class EngineImpl implements IdeScriptEngine {
    private final ScriptEngine myEngine;
    private final ClassLoader myLoader;

    EngineImpl(@NotNull ScriptEngineFactory factory, @Nullable ClassLoader loader) {
      myLoader = loader;
      myEngine = ClassLoaderUtil.computeWithClassLoader(myLoader, () -> factory.getScriptEngine());
    }

    @Override
    public Object getBinding(@NotNull String name) {
      return myEngine.get(name);
    }

    @Override
    public void setBinding(@NotNull String name, Object value) {
      myEngine.put(name, value);
    }

    @Override
    public @NotNull Writer getStdOut() {
      return myEngine.getContext().getWriter();
    }

    @Override
    public void setStdOut(@NotNull Writer writer) {
      myEngine.getContext().setWriter(writer);
    }

    @Override
    public @NotNull Writer getStdErr() {
      return myEngine.getContext().getErrorWriter();
    }

    @Override
    public void setStdErr(@NotNull Writer writer) {
      myEngine.getContext().setErrorWriter(writer);
    }

    @Override
    public @NotNull Reader getStdIn() {
      return myEngine.getContext().getReader();
    }

    @Override
    public void setStdIn(@NotNull Reader reader) {
      myEngine.getContext().setReader(reader);
    }

    @Override
    public @NotNull String getLanguage() {
      return myEngine.getFactory().getLanguageName();
    }

    @Override
    public @NotNull List<String> getFileExtensions() {
      return myEngine.getFactory().getExtensions();
    }

    @Override
    public Object eval(@NotNull String script) throws IdeScriptException {
      return ClassLoaderUtil.computeWithClassLoader(myLoader, () -> {
        try {
          return myEngine.eval(script);
        }
        catch (Throwable ex) {
          throw new IdeScriptException(ExceptionUtilRt.unwrapException(ex, ScriptException.class));
        }
      });
    }
  }

  static final class AllPluginsLoader extends ClassLoader {
    static final AllPluginsLoader INSTANCE = new AllPluginsLoader();

    final ConcurrentMap<Long, ClassLoader> myLuckyGuess = new ConcurrentHashMap<>();

    AllPluginsLoader() {
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
      long hash = StringHash.buz(base);

      Class<?> c = null;
      ClassLoader guess1 = myLuckyGuess.get(hash);   // cached loader or "this" if not found
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
          if (l == null || !hasBase && (l == guess1 || l == guess2)) continue;
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
        Map<String, URL> result = null;
        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          ClassLoader l = descriptor.getPluginClassLoader();
          Enumeration<URL> urls = l == null ? null : l.getResources(name);
          if (urls == null || !urls.hasMoreElements()) continue;
          if (result == null) result = new LinkedHashMap<>();
          while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            result.put(url.toString(), url);
          }
        }
        if (result != null) {
          return Collections.enumeration(result.values());
        }
      }
      return getClass().getClassLoader().getResources(name);
    }

    // used by kotlin engine
    public @Unmodifiable @NotNull List<URL> getUrls() {
      return JBIterable.of(PluginManagerCore.getPlugins())
        .map(PluginDescriptor::getClassLoader)
        .unique()
        .flatMap(o -> {
          try {
            //noinspection unchecked
            return (List<URL>)o.getClass().getMethod("getUrls").invoke(o);
          }
          catch (Exception e) {
            return Collections.emptyList();
          }
        })
        .unique()
        .toList();
    }
  }
}
