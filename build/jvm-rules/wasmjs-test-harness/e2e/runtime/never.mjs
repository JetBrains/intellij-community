// An awaited import that never becomes ready, so the test entrypoint never runs and no test event
// ever reaches the console: E2eOutcomesTest asserts the harness ends the run at the deadline it
// derives from TEST_TIMEOUT, as an infrastructure failure rather than "no tests executed".
export const never = new Promise(() => {});
