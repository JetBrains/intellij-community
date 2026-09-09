package org.jetbrains.jewel.bridge

import com.intellij.openapi.diagnostic.getOrHandleException
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import org.jetbrains.jewel.foundation.util.myLogger

internal interface MacScrollbarPreferences {
    fun getPreferredStyle(): Long

    fun isJumpToSpot(): Boolean

    fun observeChanges(listener: Runnable): AutoCloseable
}

internal object FfmMacScrollbarPreferences : MacScrollbarPreferences {
    private val observers = ConcurrentHashMap<Long, Observer>()

    override fun getPreferredStyle(): Long = withAutoreleasePool {
        Bindings.sendLong.invokeExact(Bindings.scroller, Bindings.preferredStyle) as Long
    }

    override fun isJumpToSpot(): Boolean = withAutoreleasePool {
        val defaults = Bindings.sendPointer.invokeExact(Bindings.defaults, Bindings.standardDefaults) as MemorySegment
        Bindings.sendBoolean.invokeExact(defaults, Bindings.synchronize) as Byte
        (Bindings.sendBooleanWithPointer.invokeExact(defaults, Bindings.boolForKey, Bindings.pagingBehavior) as Byte)
            .toInt() != 0
    }

    override fun observeChanges(listener: Runnable): AutoCloseable = withAutoreleasePool {
        val delegate = Bindings.sendPointer.invokeExact(Bindings.observerClass, Bindings.newObject) as MemorySegment
        check(delegate.address() != 0L) { "Cannot create a scrollbar observer" }
        val observer = Observer(delegate, listener)
        observers[delegate.address()] = observer
        var registered = false
        try {
            Bindings.addObserver.invokeExact(
                Bindings.localCenter,
                Bindings.addObserverSelector,
                delegate,
                Bindings.notificationSelector,
                Bindings.styleChanged,
                MemorySegment.NULL,
            )
            Bindings.addDistributedObserver.invokeExact(
                Bindings.distributedCenter,
                Bindings.addDistributedObserverSelector,
                delegate,
                Bindings.notificationSelector,
                Bindings.behaviorChanged,
                MemorySegment.NULL,
                2L,
            )
            registered = true
            observer
        } finally {
            if (!registered) observer.close()
        }
    }

    private class Observer(val delegate: MemorySegment, val listener: Runnable) : AutoCloseable {
        private val closed = AtomicBoolean()

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            observers.remove(delegate.address())
            withAutoreleasePool<Unit> {
                try {
                    Bindings.sendVoidWithPointer.invokeExact(Bindings.localCenter, Bindings.removeObserver, delegate)
                } finally {
                    try {
                        Bindings.sendVoidWithPointer.invokeExact(
                            Bindings.distributedCenter,
                            Bindings.removeObserver,
                            delegate,
                        )
                    } finally {
                        Bindings.sendVoid.invokeExact(delegate, Bindings.release)
                    }
                }
            }
        }
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    private fun notificationReceived(self: MemorySegment, selector: MemorySegment, notification: MemorySegment) {
        runCatching {
            runCatching {
                    val address = self.address()
                    val observer = observers[address] ?: return
                    SwingUtilities.invokeLater {
                        if (observers[address] === observer) {
                            runCatching { observer.listener.run() }.getOrHandleException(::reportFailure)
                        }
                    }
                }
                .getOrHandleException(::reportFailure)
        }
    }

    private fun reportFailure(failure: Throwable) {
        myLogger().warn("Cannot deliver a scrollbar notification", failure)
    }

    private fun createObserverClass(): MemorySegment =
        Arena.ofConfined().use { arena ->
            val name = arena.allocateFrom("JewelScrollbarObserver_" + UUID.randomUUID().toString().replace("-", ""))
            val observerClass = Bindings.allocateClass.invokeExact(Bindings.objectClass, name, 0L) as MemorySegment
            check(observerClass.address() != 0L) { "Cannot create the scrollbar observer class" }
            var registered = false
            try {
                val callback =
                    MethodHandles.lookup()
                        .findStatic(
                            FfmMacScrollbarPreferences::class.java,
                            "notificationReceived",
                            MethodType.methodType(
                                Void.TYPE,
                                MemorySegment::class.java,
                                MemorySegment::class.java,
                                MemorySegment::class.java,
                            ),
                        )
                val stub =
                    Bindings.linker.upcallStub(
                        callback,
                        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS),
                        Arena.global(),
                    )
                val added =
                    Bindings.addMethod.invokeExact(
                        observerClass,
                        Bindings.notificationSelector,
                        stub,
                        arena.allocateFrom("v@:@"),
                    ) as Byte
                check(added.toInt() != 0) { "Cannot add the scrollbar observer method" }
                Bindings.registerClass.invokeExact(observerClass)
                registered = true
                observerClass
            } finally {
                if (!registered) Bindings.disposeClass.invokeExact(observerClass)
            }
        }

    private inline fun <T> withAutoreleasePool(action: () -> T): T {
        val pool = Bindings.sendPointer.invokeExact(Bindings.autoreleasePool, Bindings.newObject) as MemorySegment
        try {
            return action()
        } finally {
            Bindings.sendVoid.invokeExact(pool, Bindings.drain)
        }
    }

    private object Bindings {
        val linker: Linker = Linker.nativeLinker()
        private val library =
            SymbolLookup.libraryLookup("/System/Library/Frameworks/AppKit.framework/AppKit", Arena.global())
                .or(SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global()))
        private val message = library.findOrThrow("objc_msgSend")
        private val getClass =
            linker.downcallHandle(library.findOrThrow("objc_getClass"), FunctionDescriptor.of(ADDRESS, ADDRESS))
        private val registerSelector =
            linker.downcallHandle(library.findOrThrow("sel_registerName"), FunctionDescriptor.of(ADDRESS, ADDRESS))
        val sendPointer: MethodHandle = linker.downcallHandle(message, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        private val sendPointerWithPointer =
            linker.downcallHandle(message, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS))
        val sendLong: MethodHandle = linker.downcallHandle(message, FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS))
        val sendBoolean: MethodHandle =
            linker.downcallHandle(message, FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS))
        val sendBooleanWithPointer: MethodHandle =
            linker.downcallHandle(message, FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS))
        val sendVoid: MethodHandle = linker.downcallHandle(message, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS))
        val sendVoidWithPointer: MethodHandle =
            linker.downcallHandle(message, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS))
        val addObserver: MethodHandle =
            linker.downcallHandle(
                message,
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            )
        val addDistributedObserver: MethodHandle =
            linker.downcallHandle(
                message,
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG),
            )
        val allocateClass: MethodHandle =
            linker.downcallHandle(
                library.findOrThrow("objc_allocateClassPair"),
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG),
            )
        val addMethod: MethodHandle =
            linker.downcallHandle(
                library.findOrThrow("class_addMethod"),
                FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            )
        val registerClass: MethodHandle =
            linker.downcallHandle(library.findOrThrow("objc_registerClassPair"), FunctionDescriptor.ofVoid(ADDRESS))
        val disposeClass: MethodHandle =
            linker.downcallHandle(library.findOrThrow("objc_disposeClassPair"), FunctionDescriptor.ofVoid(ADDRESS))

        val newObject = selector("new")
        val release = selector("release")
        val drain = selector("drain")
        val preferredStyle = selector("preferredScrollerStyle")
        val standardDefaults = selector("standardUserDefaults")
        val synchronize = selector("synchronize")
        val boolForKey = selector("boolForKey:")
        val removeObserver = selector("removeObserver:")
        val addObserverSelector = selector("addObserver:selector:name:object:")
        val addDistributedObserverSelector = selector("addObserver:selector:name:object:suspensionBehavior:")
        val notificationSelector = selector("notificationReceived:")
        val objectClass = objcClass("NSObject")
        val autoreleasePool = objcClass("NSAutoreleasePool")
        val scroller = objcClass("NSScroller")
        val defaults = objcClass("NSUserDefaults")
        val localCenter =
            sendPointer.invokeExact(objcClass("NSNotificationCenter"), selector("defaultCenter")) as MemorySegment
        val distributedCenter =
            sendPointer.invokeExact(objcClass("NSDistributedNotificationCenter"), selector("defaultCenter"))
                as MemorySegment
        val pagingBehavior = string("AppleScrollerPagingBehavior")
        val styleChanged = string("NSPreferredScrollerStyleDidChangeNotification")
        val behaviorChanged = string("AppleNoRedisplayAppearancePreferenceChanged")
        val observerClass = createObserverClass()

        private fun objcClass(name: String): MemorySegment =
            Arena.ofConfined().use { arena ->
                val result = getClass.invokeExact(arena.allocateFrom(name)) as MemorySegment
                check(result.address() != 0L) { "Cannot find the Objective-C class: $name" }
                result
            }

        private fun selector(name: String): MemorySegment =
            Arena.ofConfined().use { arena -> registerSelector.invokeExact(arena.allocateFrom(name)) as MemorySegment }

        private fun string(value: String): MemorySegment =
            Arena.ofConfined().use { arena ->
                val allocated = sendPointer.invokeExact(objcClass("NSString"), selector("alloc")) as MemorySegment
                sendPointerWithPointer.invokeExact(
                    allocated,
                    selector("initWithUTF8String:"),
                    arena.allocateFrom(value),
                ) as MemorySegment
            }
    }
}
