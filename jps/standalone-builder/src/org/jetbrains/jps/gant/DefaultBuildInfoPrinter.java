/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.gant;

/**
 * @author nik
 */
public class DefaultBuildInfoPrinter implements BuildInfoPrinter {
  @Override
  public void printProgressMessage(JpsGantProjectBuilder builder, String message) {
    builder.info(message);
  }

  @Override
  public void printBlockOpenedMessage(JpsGantProjectBuilder builder, String blockId) {
  }

  @Override
  public void printBlockClosedMessage(JpsGantProjectBuilder builder, String blockId) {
  }

  @Override
  public void printStatisticsMessage(JpsGantProjectBuilder builder, String key, String value) {
    builder.info("Build statistics: " + key + " = " + value);
  }

  @Override
  public void printCompilationErrors(JpsGantProjectBuilder builder, String compilerName, String messages) {
    builder.error(messages);
  }

  @Override
  public void printCompilationFinish(JpsGantProjectBuilder builder, String compilerName) {
  }

  @Override
  public void printCompilationStart(JpsGantProjectBuilder builder, String compilerName) {
  }
}
