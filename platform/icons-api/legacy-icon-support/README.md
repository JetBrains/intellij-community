## Using new icons as swing icon
Just use `Icon.toSwingIcon()` to convert the Icon to this format. 
This will actually create IconRenderer, meaning it can load images, so it is wise to reuse this and avoid recreating the Icon multiple times.
The image loader can hit cache and the result can be ok, but beware.

This should ideally be done only in low-level comments that directly interact with swing api.

## Using Swing icon inside new API
This is done by using `swingIcon` function, which will add new layer containing the swing icon.
There might be some non-implemented integrations related to specific behavior of some icons.
```kotlin
icon {
  swingIcon(AllIcons.Actions.AddDirectory)
}
```