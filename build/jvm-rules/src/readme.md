We do not depend on `"@bazel_worker_java//src/main/java/com/google/devtools/build/lib/worker:work_request_handlers"` as it leads to

```
ERROR: /private/var/tmp/_bazel_develar/c002af20f6ada3e2667e9e2ceaf2ceca/external/rules_jvm_external~~maven~maven/BUILD: no such target '@@rules_jvm_external~~maven~maven//:com_google_protobuf_protobuf_java_util': target 'com_google_protobuf_protobuf_j
```

it looks like it doesn't resolve dependencies of bazel module.

So, we copied `WorkRequestHandler` and `ProtoWorkerMessageProcessor` to our repo as a temporary solution.