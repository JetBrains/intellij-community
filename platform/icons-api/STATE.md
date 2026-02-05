
# Icons API state

* 💻 in progress
* ✅ works everywhere
* ❌ not done
* 🚶 works in compose
* 👫 works in ij-compose
* 🐌 works in swing
* ❔ unsure if planned
* 🔧 partially done

## Layers

* image ✅
* icon ✅
* row ✅
* column ✅
* animation ✅
* swingIcon ✅
* text ❌
* badge 💻

## Modifiers

* align ✅
* alpha 🚶
* color filter 🚶
* size (height/width) ✅
* margin ✅
* svg patcher 👫🐌 – uses legacy api patching
  * filters ❌
* stroke 💻

# Deferred Icons

* local ✅
* over network ❌

## Implementation

* caching 🔧 – using legacy api atm.
* loading 🔧 – using legacy api atm.
* skiko svg rendering❔
* intrinsic size calculations 🚶👫
* scaling 🚶👫
* blend modes 🔧 – only some are supported
* update/re-render dispatching 🚶👫

## Loading options
- block ✅
- blank ❌
- placeholder ❌