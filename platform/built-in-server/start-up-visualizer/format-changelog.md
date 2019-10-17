## 14

* add `project` name. Encoded using Argon to ensure that project name is not exposed.

## 13

* add `buildDate`.

## 12

* service/component events are reported in a google trace event format.
* compute own time for services/components with respect to multi-thread execution.
* `serviceWaiting` event (reported if > 100 μs to get cached value after lock).
* report all services/components without threshold.

## 11
* consistent naming of activities

## 10
* Instant events in Trace Event Format.

## 9
* icon stats.

## 8
* compute own time for services.

## 7
* add `icons`.
* split post-startup activities into dumb-aware and edt.

## 6
* add `plugin`.

## 5
* add `projectPostStartupActivities`.
* add `thread` (thread name) to activity.
* remove information about default project initialization (because default project initialization is not a sequential activity).

## 4
* `plugins` in phase name replaced to `plugin`.
* `components` in phase name replaced to `component`.

## 3
* add module level items (`module` prefix).
* add extensions (with level prefix, e.g. `appExtensions`).
* add total number of plugins, components and services (`stats`).

## 2
* add `appServices` and `projectServices`.

## 1
* initial