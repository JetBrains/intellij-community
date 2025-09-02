// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import kotlin.random.Random

private val rand = Random(fleet.util.Random.nextLong())
fun generateSeed(): Long = rand.nextLong()
  