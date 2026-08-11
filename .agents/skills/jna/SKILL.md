---
name: jna
description: Implement or debug JNA bindings in Kotlin or Java, including structures, JVM visibility, callbacks, pointers, ABI layouts, and native-library tests.
---

# JNA bindings

Treat every mapping as an ABI contract. Verify the native declaration, target architectures, and ownership rules before changing Kotlin or Java code.

## Reuse existing mappings

Search JNA Platform and the repository before declaring a native type. Prefer a maintained platform mapping when its field types, alignment, and calling convention match the target API.

## Keep reflected types accessible

JNA reflects mapped classes and fields while deriving layouts and invoking callbacks. Make every `Structure`, `Union`, callback, and related declaring class JVM-accessible from `com.sun.jna`:

- In Kotlin, use `internal` or public mapped classes; never use `private`, local, or anonymous mapped classes.
- Keep the mapped class's enclosing chain accessible too.
- Expose structure fields as real public JVM fields, normally with `@JvmField`.
- Do not assume public fields compensate for a non-public declaring class. Layout can fail with `IllegalAccessException` from `Structure.getFieldValue`.

Prefer moving visibility only as far as needed over suppressing reflection errors or opening accessibility globally.

## Match the native ABI

- Declare structure fields in native order and use `@Structure.FieldOrder`.
- Map fixed-width integers, pointers, `size_t`, native booleans, wide strings, and platform-dependent numeric types deliberately. Do not infer their size from Kotlin or Java names.
- Model embedded values with `Structure.ByValue`; model pointers separately with `Pointer` or the appropriate reference type.
- Check packing and alignment requirements instead of relying on defaults when the native declaration specifies them.
- Use the correct calling convention and library options.
- Keep callback objects and native memory strongly reachable for the full native lifetime, and release owned resources according to the native API.
- For pointer-backed structures, call `read()` and `write()` at the required ownership boundary.

## Verify without requiring native UI or permissions

Add a small JVM regression test that constructs each mapped structure and calls `size()` or otherwise forces JNA to derive its layout. Assert stable sizes or offsets only when they are defined for the tested architecture.

Keep permission-, hardware-, and OS-dependent native integration coverage separate. A layout test should catch visibility and field-order failures before a native callback reaches production code.

When debugging, retain the complete cause chain: wrapper errors often hide the useful `IllegalAccessException`, invalid field type, alignment error, or callback exception underneath.
