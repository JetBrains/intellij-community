// "Make 'K' implement 'java.lang.Runnable'" "true"

interface RemoteStore<K extends Runnable, V>{}
class BackedRemoteStore<K extends Runnable, V> implements RemoteStore<K, V> {}
