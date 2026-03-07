# Cross frontend-api Icons
These Icons support being rendered by multiple frontend-apis (swing, compose, can be extended)

## Data & Rendering split
The API is split to two parts:
- [Data](.)
- [Rendering](./rendering)

This allows declaring icons without depending on the rendering implementation, which is useful on the backend.

## `org.jetbrains.icons.api.Icon`
This is data part, an Icon, which represents how the specific Icon should look like. 
This should be serializable (atleast in the IntelliJ implementation) and sendable directly via RPC.
Creating this class should not generate any side-effects or long-loading of resources.

```kotlin
val githubIcon = icon {
    image("icons/github.svg", ShowcaseIcons::class.java.classLoader, modifier = IconModifier.fillMaxSize())
}
```

Layouting is also supported, where you can layer more icons into one:
```kotlin
val githubIcon = imageIcon("icons/github.svg", javaClass.classLoader)
val gitIcon = imageIcon("icons/git.svg", javaClass.classLoader)

val layeredIcon = icon {
    icon(githubIcon)
    icon(gitIcon)
}

val rowLayeredIcon = icon {
    column {
        row {
          icon(githubIcon)
          icon(gitIcon)
        }
        row {
          icon(githubIcon)
          icon(gitIcon)
        }
    }
}
```
Row & Column behaves similarly to Compose counterparts.
There is also IconModifiers, that can affect how the resulting Icon will look like, 
again, concept from Compose, you can use modifiers to adjust:
- Layouting (margin, size, align
- Color filtering
- Svg replacements

The Icons are going to infer expected size (based on settings, svg data or image data), 
however, you can also make the icons scaled to the container component.

To serialize an icon, serializers module can be obtained from the IconManager.

## `org.jetbrains.icons.api.rendering.IconRenderer`
While having data object for Icon is great, we still need a way to render it.
This class is responsible for getting the previously mentioned Icon data object,
it figures out how to load the used images. 
Creating this class actually loads resources, so it should be considered a heavy operation.

This normally shouldn't be used directly, but rather via components, like the Jewel `fun Icon(..)` icon, or specific swing components,
that create the renderer themselves. 
To create a renderer for an Icon, you should use Icon.createRenderer() function.

The final size of the Icon is inferred from the Icon data object but also affected by the scale factor and the containing component.
In compose, the size is affected by modifiers applied to the `Icon()` composable, the swing conunterpart can be configured via the
toSwingIcon() function. (or will be in the future)

## Legacy/Swing Icon interop
To use Swing icons inside the new icons/with the new api, check [legacy-icon-support](./legacy-icon-support/)

## `org.jetbrains.icons.api.IconManager`
This interface is responsible for generating Icon models.

## Extensibility
All extensions should be registered beforehand, to allow deserialization, where we need to know
all possible layers, loaders, etc. to properly deserialize icons. That is why it is not enough to 
just pass your loaders/layers to the IconManager or whatever when creating the icon.

### Custom Layer
Custom layers can be easily added by designer's custom function. However, make sure to also register
the layer in the IconManager, to make sure it is serializable, also, ensure to register corresponding Icon Layer Renderer.

### ImageResourceLoader
Custom image resource loaders can be added.
To do so, you need to implement `org.jetbrains.icons.api.ImageResourceLoader` interface and
then register it via the appropriate IconManager, check individual managers for details.
The registration will tell the IconManager how to serialize this loader and will let
ImageResourceProvider know how to load the resources.
Make sure to implement equals and hashCode functions, as they are used for caching purposes.

Also, introduce an extension function for IconDesigner to allow easy creation of your new loader.

## Implementation details
For implementation details, refer to [implementation modules](../icons-impl/README.md)