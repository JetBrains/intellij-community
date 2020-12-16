# 27

 * `classLoading` changed — `time` includes class define time, `defineTime` and `searchTime` added. `searchTime` it is property that computed for convenience, — not measured but computed as `time - defineTime`. 
 * `resourceLoading` with the same schema as `classLoading` added (except `searchTime`).

# 26

* `stats.loadedClasses` map (plugin id to class count) is removed.
* `plugins` is added. `classCount` for number of classes, `classLoadingEdtTime`, `classLoadingBackgroundTime` 

# 25

* add `classLoading`. Only if `idea.record.classloading.stats=true` is specified.

# 24

* add `icons.action-icon`.

# 23

* `svg-decode` doesn't include `svg-cache-read` anymore.

# 22

* `project dumb post-startup` -> `project post-startup`

# 21

* `tasks waiting` removed. No such activity anymore.

# 20

* new compact format for service events.

# 19

* add `eua showing` (to separate `showUserAgreementAndConsentsIfNeeded` from `config importing`).

# 18

* rename `plugin descriptors loading` in application loader to `plugin descriptor init waiting`.
* change `cds` field type from `string` to `boolean`.

# 17

* add `plugin descriptor loading`.

# 16

* add `RunManager initialization`.
* add `projectComponentCreated event handling`.
* add `projectComponentCreated`.

## 15

* add `project frame assigning` (`APP_INIT`).
* add `placing calling projectOpened on event queue` (main category).
* more correct measurement of project start-up activities (logic how do we execute start-up activities was fully reworked).

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