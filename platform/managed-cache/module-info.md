# Managed Cache

[ManagedCache](../platform-impl/src/com/intellij/util/io/cache/ManagedCache.kt) is an interface that has a standardized, project-scoped caching mechanism for persistent binary data with versioning support.

## Implementations
Currently, there are two implementations of `ManagedCache`:
1. [ManagedPersistentCache](../platform-impl/src/com/intellij/util/io/cache/ManagedPersistentCache.kt) — an implementation that uses [PersistentMapImpl](../util/src/com/intellij/util/io/PersistentMapImpl.java) as a storage.
2. [RemoteCache](../../../remote-dev/cwm-guest/src/com/jetbrains/thinclient/util/RemoteCache.kt) — a frontend-only implementation that uses [RemoteManagedCacheApi](managed.cache/src/RemoteManagedCacheApi.kt) to look up and store data on the backend side.

So, it could be used in Local and Remote development scenarios with the same API.

## Usage
`ManagedCache` could be used for persistent caching binary-serializable project-wide data, when the versioning is needed for backward compatability.

`ManagedCache` could be constructed via [ManagedCacheFactory.createCache](../platform-impl/src/com/intellij/util/io/cache/ManagedCacheFactory.kt) method.
It accepts a cache name, (de-)serializers for key and value, a current version of the (de-)serialization protocol.
The version should be incremented on any change in the serialization protocol, e.g., adding a new field or changing the type of the existing one.

## Example usages
The current only usage of `ManagedCache` is [GraveImpl](../platform-impl/src/com/intellij/openapi/editor/impl/zombie/GraveImpl.kt), which uses `ManagedCache` as an abstraction for storing caches. 