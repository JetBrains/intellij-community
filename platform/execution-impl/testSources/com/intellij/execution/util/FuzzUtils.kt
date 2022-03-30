/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.util

import kotlin.random.Random

// Utilities to create random values used for fuzz testing. See
// [com.intellij.execution.filters.GenericFileFilterTest.`fuzz test`] for example usage.

/** Creates a generator function that chooses one of the options from the given iterable randomly. */
fun <T> oneOf(options: Iterable<T>): Random.() -> T = { options.toList().let { it[nextInt(it.size)] } }

/** Creates a generator function that delegate a randomly chosen generator from the given options. */
fun <T> oneOf(vararg options: Random.() -> T): Random.() -> T = { options[nextInt(options.size)]() }

/** Creates a generator that randomly generates an integer. */
fun someInt(range: IntRange = 1..100): Random.() -> Int = { range.random(this) }

/** Creates a generator that invokes the given generator some random number of times and concatenate the [toString] values. */
fun <T> (Random.() -> T).repeated(times: IntRange = 0..10, separator: String = ""): Random.() -> String = {
  (0 until times.random(this)).joinToString(separator) { this@repeated().toString() }
}

/** Creates a generator that delegates the given generator and apply some side effects to the passed in consumer. */
fun <T> (Random.() -> T).useGenerated(consumer: (T) -> Unit): Random.() -> T = { this@useGenerated().also(consumer) }

/** Creates a generator that invokes the given two generators and concatenate their results together as strings. */
operator fun <T> (Random.() -> T).plus(that: Random.() -> T): Random.() -> String = { this@plus().toString() + that().toString() }

/** Creates a generator that invokes the given generator and append its result with a given object as string. */
operator fun <T> (Random.() -> T).plus(that: Any): Random.() -> String = { this@plus().toString() + that.toString() }

/** Creates a generator that invokes the given generator and prepend its result with a given object as string. */
operator fun <T> Any.plus(that: Random.() -> T): Random.() -> String = { this@plus.toString() + that() }

