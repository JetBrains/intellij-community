package org.jetbrains.jewel.foundation.util

import java.lang.reflect.Method
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger
import org.jetbrains.annotations.ApiStatus

public inline fun <reified T : Any> T.myLogger(): JewelLogger = JewelLogger.getInstance(T::class.java)

/** A wrapper which uses either IDE logging subsystem (if available) or java.util.logging. */
@ApiStatus.NonExtendable
@Suppress("OptionalUnit")
public abstract class JewelLogger {
    private interface Factory {
        fun getInstance(category: String?): JewelLogger
    }

    public fun trace(message: String?): Unit = trace(message, null)

    public fun trace(t: Throwable): Unit = trace(t.message, t)

    public fun debug(message: String?): Unit = debug(message, null)

    public fun debug(t: Throwable): Unit = debug(t.message, t)

    public fun info(message: String?): Unit = info(message, null)

    public fun info(t: Throwable): Unit = info(t.message, t)

    public fun warn(message: String?): Unit = warn(message, null)

    public fun warn(t: Throwable): Unit = warn(t.message, t)

    public fun error(message: String?): Unit = error(message, null)

    public fun error(t: Throwable): Unit = error(t.message, t)

    public abstract fun trace(message: String?, t: Throwable?)

    public abstract fun debug(message: String?, t: Throwable?)

    public abstract fun info(message: String?, t: Throwable?)

    public abstract fun warn(message: String?, t: Throwable?)

    public abstract fun error(message: String?, t: Throwable?)

    private class JavaFactory : Factory {
        override fun getInstance(category: String?): JewelLogger {
            val logger by lazy {
                val l = Logger.getLogger(category)

                // Remove existing default handlers to avoid duplicate messages in console
                for (handler in l.handlers) {
                    l.removeHandler(handler)
                }

                // Create a new handler with a higher logging level
                val handler = ConsoleHandler()
                handler.level = Level.FINE
                l.addHandler(handler)

                // Tune the logger for level and duplicated messages
                l.level = Level.FINE
                l.useParentHandlers = false
                l
            }
            return object : JewelLogger() {
                override fun trace(message: String?, t: Throwable?) {
                    logger.log(Level.FINER, message, t)
                }

                override fun debug(message: String?, t: Throwable?) {
                    logger.log(Level.FINE, message, t)
                }

                override fun info(message: String?, t: Throwable?) {
                    logger.log(Level.INFO, message, t)
                }

                override fun warn(message: String?, t: Throwable?) {
                    logger.log(Level.WARNING, message, t)
                }

                override fun error(message: String?, t: Throwable?) {
                    logger.log(Level.SEVERE, message, t)
                }
            }
        }
    }

    private abstract class ReflectionBasedFactory : Factory {
        @Throws(RuntimeException::class)
        @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
        override fun getInstance(category: String?): JewelLogger {
            try {
                val logger = getLogger(category)

                return object : JewelLogger() {
                    override fun trace(message: String?, t: Throwable?) {
                        try {
                            this@ReflectionBasedFactory.trace(message, t, logger)
                        } catch (_: Exception) {}
                    }

                    override fun debug(message: String?, t: Throwable?) {
                        try {
                            this@ReflectionBasedFactory.debug(message, t, logger)
                        } catch (_: Exception) {}
                    }

                    override fun info(message: String?, t: Throwable?) {
                        try {
                            this@ReflectionBasedFactory.info(message, t, logger)
                        } catch (_: Exception) {}
                    }

                    override fun warn(message: String?, t: Throwable?) {
                        try {
                            this@ReflectionBasedFactory.warn(message, t, logger)
                        } catch (_: Exception) {}
                    }

                    override fun error(message: String?, t: Throwable?) {
                        try {
                            this@ReflectionBasedFactory.error(message, t, logger)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        @Throws(Exception::class) abstract fun trace(message: String?, t: Throwable?, logger: Any?)

        @Throws(Exception::class) abstract fun debug(message: String?, t: Throwable?, logger: Any?)

        @Throws(Exception::class) abstract fun error(message: String?, t: Throwable?, logger: Any?)

        @Throws(Exception::class) abstract fun warn(message: String?, t: Throwable?, logger: Any?)

        @Throws(Exception::class) abstract fun info(message: String?, t: Throwable?, logger: Any?)

        @Throws(Exception::class) abstract fun getLogger(category: String?): Any
    }

    private class IdeaFactory : ReflectionBasedFactory() {
        private val myGetInstance: Method
        private val ideaTrace: Method
        private val ideaDebug: Method
        private val ideaInfo: Method
        private val ideaWarn: Method
        private val ideaError: Method

        init {
            val loggerClass = Class.forName("com.intellij.openapi.diagnostic.Logger")
            myGetInstance = loggerClass.getMethod("getInstance", String::class.java)
            myGetInstance.isAccessible = true
            ideaTrace = loggerClass.getMethod("trace", Throwable::class.java)
            ideaTrace.isAccessible = true
            ideaDebug = loggerClass.getMethod("debug", String::class.java, Throwable::class.java)
            ideaDebug.isAccessible = true
            ideaInfo = loggerClass.getMethod("info", String::class.java, Throwable::class.java)
            ideaInfo.isAccessible = true
            ideaWarn = loggerClass.getMethod("warn", String::class.java, Throwable::class.java)
            ideaWarn.isAccessible = true
            ideaError = loggerClass.getMethod("error", String::class.java, Throwable::class.java)
            ideaError.isAccessible = true
        }

        @Throws(Exception::class)
        override fun trace(message: String?, t: Throwable?, logger: Any?) {
            ideaTrace.invoke(logger, t)
        }

        @Throws(Exception::class)
        override fun debug(message: String?, t: Throwable?, logger: Any?) {
            ideaDebug.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun error(message: String?, t: Throwable?, logger: Any?) {
            ideaError.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun warn(message: String?, t: Throwable?, logger: Any?) {
            ideaWarn.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun info(message: String?, t: Throwable?, logger: Any?) {
            ideaInfo.invoke(logger, message, t)
        }

        @Throws(Exception::class) override fun getLogger(category: String?): Any = myGetInstance.invoke(null, category)
    }

    private class Slf4JFactory : ReflectionBasedFactory() {
        private val myGetLogger: Method
        private val myTrace: Method
        private val myDebug: Method
        private val myInfo: Method
        private val myWarn: Method
        private val myError: Method

        init {
            val loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory")
            myGetLogger = loggerFactoryClass.getMethod("getLogger", String::class.java)
            myGetLogger.isAccessible = true

            val loggerClass = Class.forName("org.slf4j.Logger")
            myTrace = loggerClass.getMethod("trace", String::class.java, Throwable::class.java)
            myTrace.isAccessible = true
            myDebug = loggerClass.getMethod("debug", String::class.java, Throwable::class.java)
            myDebug.isAccessible = true
            myInfo = loggerClass.getMethod("info", String::class.java, Throwable::class.java)
            myInfo.isAccessible = true
            myWarn = loggerClass.getMethod("warn", String::class.java, Throwable::class.java)
            myWarn.isAccessible = true
            myError = loggerClass.getMethod("error", String::class.java, Throwable::class.java)
            myError.isAccessible = true
        }

        @Throws(Exception::class)
        override fun trace(message: String?, t: Throwable?, logger: Any?) {
            myTrace.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun debug(message: String?, t: Throwable?, logger: Any?) {
            myDebug.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun error(message: String?, t: Throwable?, logger: Any?) {
            myError.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun warn(message: String?, t: Throwable?, logger: Any?) {
            myWarn.invoke(logger, message, t)
        }

        @Throws(Exception::class)
        override fun info(message: String?, t: Throwable?, logger: Any?) {
            myInfo.invoke(logger, message, t)
        }

        @Throws(Exception::class) override fun getLogger(category: String?): Any = myGetLogger.invoke(null, category)
    }

    public companion object {
        @get:Synchronized private val factory: Factory = createFactory()

        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        private fun createFactory(): Factory =
            try {
                IdeaFactory()
            } catch (_: Throwable) {
                try {
                    Slf4JFactory()
                } catch (_: Throwable) {
                    JavaFactory()
                }
            }

        public fun getInstance(category: String): JewelLogger = factory.getInstance("#$category")

        public fun getInstance(clazz: Class<*>): JewelLogger = getInstance("#${clazz.name}")
    }
}
