fun test1(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (42)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 42
      else -> return when (num) {
        42 -> {
          println(42)
          return ((42))
        }
        else -> {
          println("kotlin")
          num.let { return@let 0 }
          num.let { return 42 }
          42
        }
      }
    })
  } else 42
}

fun test2(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (0)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 42
      else -> return when (num) {
        42 -> {
          println(42)
          return ((42))
        }
        else -> {
          println("kotlin")
          num.let { return 42 }
          42
        }
      }
    })
  } else 42
}

fun test3(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (42)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 0
      else -> return when (num) {
        42 -> {
          println(42)
          return ((42))
        }
        else -> {
          println("kotlin")
          num.let { return 42 }
          42
        }
      }
    })
  } else 42
}

fun test4(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (42)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 42
      else -> return when (num) {
        42 -> {
          println(42)
          return ((0))
        }
        else -> {
          println("kotlin")
          num.let { return 42 }
          42
        }
      }
    })
  } else 42
}

fun test5(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (42)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 42
      else -> return when (num) {
        42 -> {
          println(42)
          return ((42))
        }
        else -> {
          println("kotlin")
          num.let { return 0 }
          42
        }
      }
    })
  } else 42
}

fun test6(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (42)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 42
      else -> return when (num) {
        42 -> {
          println(42)
          return ((42))
        }
        else -> {
          println("kotlin")
          num.let { return 42 }
          0
        }
      }
    })
  } else 42
}

fun test7(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    println("Hello, World!")
    return (42)
  }
  return if (flag2) {
    println("blah blah blah")
    (when (num) {
      42 -> 42
      else -> return when (num) {
        42 -> {
          println(42)
          return ((42))
        }
        else -> {
          println("kotlin")
          num.let { return 42 }
          42
        }
      }
    })
  } else 0
}

inline fun <T, R> T.let(block: (T) -> R): R {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  return block(this)
}