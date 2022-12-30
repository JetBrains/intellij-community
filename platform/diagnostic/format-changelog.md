## 39

* add `memory` extra data with `usedMb`, `maxMb` and `metaspaceMb` numbers

## 38

* `classLoading` changed - added `preparedCount` and `loadedCount` metrics on Linux 

## 37

* add `langLoading` extra data with `elementTypeCount` number

## 36
* Do not write empty class loading stats for `plugins` (skipped if `loadedClassCount` is equal to `0`).

## 35
* rename `entities loading` to `loading entities from files`
* rename `project model changes saving (in queue)` to `applying loaded changes (in queue)`
* rename `project model changes saving` to `applying loaded changes`
* rename `project libraries loading` to `libraries instantiation`
* rename `events modules added` to `firing modules_added event`
* rename `module cache loading` to `cache loading`
* rename `events sending after modules are loaded` to `postponed events sending`

## 34
* add `module cache loading`
* `moduleLoading` split to `modules loading without cache` and `modules loading with cache`
* add `project files loading`, `serializers creation`, `unloaded modules loading`, `entities loading`, `project model changes saving (in queue)` and `project model changes saving`
* add `modules instantiation` and `project libraries loading`
* add `events sending after modules are loaded`, `events sending (in queue)` and `events sending`
* add `events modules added`

## 33
 * rename `init JBUIScale` to `scale initialization`.
 * add `base LaF creation`, `base LaF initialization`, `base LaF defaults getting`.

## 32
 * `prepareAppInitActivities` is removed in favour of `items`.
 * `totalDurationComputed` is removed.
 * `totalDurationActual` is renamed to `totalDuration`.

## 31
 * rename `add registry keys` to `registry keys adding`.

## 30
 * add `preloading activity executing`.

## 29
 * Add `splash preparation (in queue)` and `splash initialization (in queue)`.
 * Add `pre app class running`, `repaint manager set`, `linux wm set`. Existing`event queue replacing` doesn't include added metrics anymore.
 * Remove `icon loader activation`.

## 28
* Add `platform plugin collecting`, `plugin from classpath loading`, `plugin from user dir loading`, `plugin from bundled dir loading`.
* Add `appStarter set`, `appStarter start-up`, `appStarter about`.

## 27
 * `classLoading` changed — `time` includes class define time, `defineTime` and `searchTime` added. `searchTime` it is property that computed for convenience, — not measured but computed as `time - defineTime`. 
 * `resourceLoading` with the same schema as `classLoading` added (except `searchTime`).

## 26

* `stats.loadedClasses` map (plugin id to class count) is removed.
* `plugins` is added. `classCount` for number of classes, `classLoadingEdtTime`, `classLoadingBackgroundTime` 

## 25

* add `classLoading`. Only if `idea.record.classloading.stats=true` is specified.

## 24

* add `icons.action-icon`.

## 23

* `svg-decode` doesn't include `svg-cache-read` anymore.

## 22

* `project dumb post-startup` -> `project post-startup`

## 21

* `tasks waiting` removed. No such activity anymore.

## 20

* new compact format for service events.

## 19

* add `eua showing` (to separate `showUserAgreementAndConsentsIfNeeded` from `config importing`).

## 18

* rename `plugin descriptors loading` in application loader to `plugin descriptor init waiting`.
* change `cds` field type from `string` to `boolean`.

## 17

* add `plugin descriptor loading`.

## 16

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

* service/component events are reported in a Google trace event format.
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