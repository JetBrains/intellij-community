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
 * @deprecated see {@link JpsGantProjectBuilder} for details
 */
public interface BuildInfoPrinter {

  void printProgressMessage(JpsGantProjectBuilder builder, String message);

  void printBlockOpenedMessage(JpsGantProjectBuilder builder, String blockId);

  void printBlockClosedMessage(JpsGantProjectBuilder builder, String blockId);

  void printStatisticsMessage(JpsGantProjectBuilder builder, String key, String value);

  void printCompilationErrors(JpsGantProjectBuilder builder, String compilerName, String messages);

  void printCompilationFinish(JpsGantProjectBuilder builder, String compilerName);

  void printCompilationStart(JpsGantProjectBuilder builder, String compilerName);
}
