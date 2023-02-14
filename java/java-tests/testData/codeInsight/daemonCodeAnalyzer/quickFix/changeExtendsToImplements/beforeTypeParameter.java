// "Make 'K' implement 'java.lang.Runnable'" "true-preview"

interface RemoteStore<K extends Runnable, V>{}
class BackedRemoteStore<K, V> implements RemoteStore<<caret>K, V> {}
