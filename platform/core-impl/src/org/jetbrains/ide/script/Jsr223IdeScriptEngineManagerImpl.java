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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

class Jsr223IdeScriptEngineManagerImpl extends IdeScriptEngineManager {
  private static final Logger LOG = Logger.getInstance(IdeScriptEngineManager.class);

  private final Future<ScriptEngineManager> myManagerFuture = PooledThreadExecutor.INSTANCE.submit(new Callable<ScriptEngineManager>() {
    @Override
    public ScriptEngineManager call() {
      long start = System.currentTimeMillis();
      try {
        return new ScriptEngineManager();
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
  public IdeScriptEngine getEngineForLanguage(@NotNull String language) {
    ScriptEngine engine = getScriptEngineManager().getEngineByName(language);
    return createIdeScriptEngine(engine);
  }

  @Nullable
  @Override
  public IdeScriptEngine getEngineForFileExtension(@NotNull String extension) {
    ScriptEngine engine = getScriptEngineManager().getEngineByExtension(extension);
    return createIdeScriptEngine(engine);
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
    engine.setStdOut(new MyAbstractWriter() {
      @Override
      public void write(char[] cbuf, int off, int len) throws IOException {
        LOG.info(new String(cbuf, off, len));
      }
    });
    engine.setStdErr(new MyAbstractWriter() {
      @Override
      public void write(char[] cbuf, int off, int len) throws IOException {
        LOG.warn(new String(cbuf, off, len));
      }
    });
    return engine;
  }

  static class Jsr223IdeScriptEngine implements IdeScriptEngine {
    private final ScriptEngine myEngine;

    Jsr223IdeScriptEngine(ScriptEngine engine) {
      myEngine = engine;
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
    public Object eval(@NotNull String script) throws IdeScriptException {
      try {
        return myEngine.eval(script);
      }
      catch (Throwable e) {
        throw new IdeScriptException(e);
      }
    }
  }

  private static abstract class MyAbstractWriter extends Writer {
    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
    }
  }
}
