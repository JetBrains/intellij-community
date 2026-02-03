// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

/**
 * Marker interface for inspections that aggregate results from multiple sources or inspections.
 * <p>
 * This interface allows a single inspection to export problem descriptors with different inspection IDs,
 * based on the problem group specified in each descriptor.
 * </p>
 *
 * <h2>Examples</h2>
 * <pre>
 * An inspection "MyAggregatorInspection" creates three problem descriptors:
 *   • Descriptor A with ProblemGroup("JavaDocInspection")
 *     → Exported as "JavaDocInspection" result
 *   • Descriptor B with ProblemGroup("NamingConventionInspection")
 *     → Exported as "NamingConventionInspection" result
 *   • Descriptor C with ProblemGroup("UnknownTool")
 *     → Falls back to "MyAggregatorInspection" (tool not found in context)
 * </pre>
 */
public interface AggregateResultsInspection { }
