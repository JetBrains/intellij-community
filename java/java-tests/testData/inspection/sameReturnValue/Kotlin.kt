fun <warning descr="Method 'test1()' always returns '42'">test1</warning>(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (42)
  }
  return if (flag2) {
    (when (num) {
      42 -> 42
      else -> return when (num) {
        0 -> {
          ((42))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 42 }
          }
          42
        }
      }
    })
  } else 42
}

fun test2(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (0)
  }
  return if (flag2) {
    (when (num) {
      42 -> 42
      else -> return when (num) {
        0 -> {
          ((42))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 42 }
          }
          42
        }
      }
    })
  } else 42
}

fun test3(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (42)
  }
  return if (flag2) {
    (when (num) {
      42 -> 0
      else -> return when (num) {
        0 -> {
          ((42))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 42 }
          }
          42
        }
      }
    })
  } else 42
}

fun test4(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (42)
  }
  return if (flag2) {
    (when (num) {
      42 -> 0
      else -> return when (num) {
        0 -> {
          ((0))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 42 }
          }
          42
        }
      }
    })
  } else 42
}

fun test5(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (42)
  }
  return if (flag2) {
    (when (num) {
      42 -> 0
      else -> return when (num) {
        0 -> {
          ((42))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 0 }
          }
          42
        }
      }
    })
  } else 42
}

fun test6(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (42)
  }
  return if (flag2) {
    (when (num) {
      42 -> 0
      else -> return when (num) {
        0 -> {
          ((0))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 42 }
          }
          0
        }
      }
    })
  } else 42
}

fun test7(num: Int, flag1: Boolean, flag2: Boolean): Int {
  if (flag1) {
    return (42)
  }
  return if (flag2) {
    (when (num) {
      42 -> 0
      else -> return when (num) {
        0 -> {
          ((42))
        }
        else -> {
          if (num % 17 == 0) {
            num.let { return@let 1 }
            num.let { return 42 }
          }
          42
        }
      }
    })
  } else 0
}

inline fun <T, R> T.let(block: (T) -> R): R {
  return block(this)
}