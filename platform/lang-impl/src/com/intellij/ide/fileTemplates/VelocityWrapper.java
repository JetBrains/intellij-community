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
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.log.LogSystem;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.ResourceManager;
import org.apache.velocity.runtime.resource.ResourceManagerImpl;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * Initializes Velocity when it's actually needed. All interaction with Velocity should go through this class.
 *
 * @author peter
 */
class VelocityWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.VelocityWrapper");
  private static final ThreadLocal<FileTemplateManager> ourTemplateManager = new ThreadLocal<>();

  static {
    try{
      final Class<?>[] interfaces = ResourceManagerImpl.class.getInterfaces();
      if (interfaces.length != 1 || !interfaces[0].equals(ResourceManager.class)) {
        throw new IllegalStateException("Incorrect velocity version in the classpath" +
                                        ", ResourceManager in " + PathManager.getJarPathForClass(ResourceManager.class) +
                                        ", ResourceManagerImpl in " + PathManager.getJarPathForClass(ResourceManagerImpl.class));
      }

      LogSystem emptyLogSystem = new LogSystem() {
        @Override
        public void init(RuntimeServices runtimeServices) throws Exception {
        }

        @Override
        public void logVelocityMessage(int i, String s) {
          //todo[myakovlev] log somethere?
        }
      };
      Velocity.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, emptyLogSystem);
      Velocity.setProperty(RuntimeConstants.INPUT_ENCODING, FileTemplate.ourEncoding);
      Velocity.setProperty(RuntimeConstants.PARSER_POOL_SIZE, 3);
      Velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "includes");
      Velocity.setProperty("includes.resource.loader.instance", new ResourceLoader() {
        @Override
        public void init(ExtendedProperties configuration) {
        }

        @Override
        public InputStream getResourceStream(String resourceName) throws ResourceNotFoundException {
          FileTemplateManager templateManager = ourTemplateManager.get();
          if (templateManager == null) templateManager = FileTemplateManager.getDefaultInstance();
          final FileTemplate include = templateManager.getPattern(resourceName);
          if (include == null) {
            throw new ResourceNotFoundException("Template not found: " + resourceName);
          }
          final String text = include.getText();
          try {
            return new ByteArrayInputStream(text.getBytes(FileTemplate.ourEncoding));
          }
          catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public boolean isSourceModified(Resource resource) {
          return true;
        }

        @Override
        public long getLastModified(Resource resource) {
          return 0L;
        }
      });

      Thread thread = Thread.currentThread();
      ClassLoader classLoader = thread.getContextClassLoader();
      Application application = ApplicationManager.getApplication();

      if (application.isInternal() && !application.isUnitTestMode() &&
          SystemInfo.isJavaVersionAtLeast("1.6.0_33") && SystemInfo.isAppleJvm) {
        thread.setContextClassLoader(FileTemplate.class.getClassLoader());
      }

      try {
        Velocity.init();
      }
      finally {
        thread.setContextClassLoader(classLoader);
      }
    }
    catch (Exception e){
      LOG.error("Unable to init Velocity", e);
    }
  }

  static SimpleNode parse(Reader reader, String templateName) throws ParseException {
    return RuntimeSingleton.parse(reader, templateName);
  }

  static boolean evaluate(@Nullable Project project, Context context, Writer writer, String templateContent)
    throws ParseErrorException, MethodInvocationException, ResourceNotFoundException {
    try {
      ourTemplateManager.set(project == null ? FileTemplateManager.getDefaultInstance() : FileTemplateManager.getInstance(project));
      return Velocity.evaluate(context, writer, "", templateContent);
    }
    finally {
      ourTemplateManager.set(null);
    }
  }

}
