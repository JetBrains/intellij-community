// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs


inline fun LongIterator.forEach(action: (Long) -> Unit) {
  while(this.hasNext()) action(this.next())
}