# Pattern B — Extension-point / message-bus listener with a strong `this` capture

This pattern applies at a **retention boundary** where an application-scope container (`ExtensionPointImpl.listeners`, `MessageBusImpl.subscribers`) legitimately outlives a project-scope service by design. That is a category (A) case — the app-scope container is *supposed* to live longer than any single project. `WeakReference` here weakens the reference *at the boundary between scopes*, on the lambda's captured `this`. It does not weaken the service's own `myProject` field (see the anti-pattern in `SKILL.md`).

If your chain does not go through `ExtensionPointImpl.listeners` / `MessageBusImpl.subscribers` and instead terminates in `ObjectTree.myObject2ParentNode` or a project-scope holder, this is not Pattern B — do not apply this fix. Go back to the "Root cause first" section of `SKILL.md`.

**Root cause.** A project-scoped service registered a listener on an application-scoped extension point using `addChangeListener(listener, parentDisposable)`. `ExtensionPointImpl.addChangeListener` adds the adapter to `listeners` **immediately** and only removes it in a `Disposer.register(parentDisposable) { removeExtensionPointListener(adapter) }` cleanup. If that cleanup does not run (or runs after the leak check), the adapter — and everything the listener lambda captures, including the service and its `Project` field — stays alive at the application scope.

**Preferred fix.** First, verify that removing / unregistering the listener at project dispose is genuinely not feasible (usually because the `parentDisposable` disposal path is not under your control). Only then, break the strong `this` capture by wrapping in a `WeakReference` at the lambda boundary. Example (from `CodeStyleSettingsManager.java:200-209`, before/after):

```java
// BEFORE
protected void registerExtensionPointListeners(@Nullable Disposable disposable) {
  FileIndentOptionsProvider.EP_NAME.addChangeListener(this::notifyCodeStyleSettingsChanged, disposable);
  ...
}

// AFTER
protected void registerExtensionPointListeners(@Nullable Disposable disposable) {
  // WeakReference indirection: the app-scoped extension point outlives project-scoped subclasses,
  // so a missed disposal here would pin the manager (and its Project) via the listener list.
  java.lang.ref.WeakReference<CodeStyleSettingsManager> selfRef = new java.lang.ref.WeakReference<>(this);
  FileIndentOptionsProvider.EP_NAME.addChangeListener(() -> {
    CodeStyleSettingsManager self = selfRef.get();
    if (self != null) self.notifyCodeStyleSettingsChanged();
  }, disposable);
  ...
}
```

Why this is safe: while the service container holds a strong reference to the manager, `selfRef.get()` returns non-null and the listener behaves identically. After container release (project dispose for a project service), the WeakReference can clear and the extension point's listener list no longer pins the manager. Same trick applies to anonymous-inner-class listeners — pull the enclosing `this` into a `WeakReference` local and use `ref.get()` inside every callback method.

**Do not** simply remove `addChangeListener`. The listener has a real behavioural purpose; you must preserve that when the service is alive.
