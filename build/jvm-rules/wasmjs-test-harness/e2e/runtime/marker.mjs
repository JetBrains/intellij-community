// Mirrors the skiko runtime-file shape: served under /_runtime/ with the module-adjacent URL
// remapped through the import map (module_runtime_files), and exposing a readiness promise the
// page awaits before the test entrypoint (awaited_imports).
export const marker = "module-adjacent";
export const ready = Promise.resolve().then(() => {
  globalThis.__markerReady = true;
});
