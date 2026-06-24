# Modifier Order

Compose applies modifiers **outside-in**: the first modifier in the chain is the outermost,
wrapping everything that follows.

```kotlin
Modifier
    .padding(16.dp)        // outer — applied first
    .background(Color.Red) // inner — applied inside the padding
```

The box above has red background **inside** the padded area. If reversed:

```kotlin
Modifier
    .background(Color.Red) // outer — fills entire allocated space
    .padding(16.dp)        // inner — content is padded, background is not
```

The background extends to the edge; only the content is padded.

## Recommended order

1. **Size and constraints** — `fillMaxSize()`, `width()`, `height()`, `sizeIn()`
2. **Padding and spacing** — `padding()`, `offset()`
3. **Draw modifiers** — `background()`, `border()`, `clip()`, `shadow()`
4. **Transform modifiers** — `graphicsLayer { }`
5. **Pointer input** — `clickable()`, `hoverable()`, `pointerInput()`
6. **Semantics** — `semantics { }`

## Common mistakes

### Clickable hit area too small

```kotlin
// WRONG — clickable is inside padding, so hit area excludes padding
Modifier
    .padding(16.dp)
    .clickable { }

// RIGHT — clickable wraps padding, hit area includes padding
Modifier
    .clickable { }
    .padding(16.dp)
```

### Background clipped unexpectedly

```kotlin
// WRONG — clip is outside background, so background draws outside the clip
Modifier
    .clip(RoundedCornerShape(8.dp))
    .background(Color.Red)

// RIGHT — background is inside clip
Modifier
    .background(Color.Red)
    .clip(RoundedCornerShape(8.dp))
```

### Transform not applied to content

```kotlin
// WRONG — graphicsLayer is inside padding, so padding is not transformed
Modifier
    .padding(16.dp)
    .graphicsLayer { rotationZ = 45f }

// RIGHT — graphicsLayer wraps padding
Modifier
    .graphicsLayer { rotationZ = 45f }
    .padding(16.dp)
```

## Pointer input and semantics

Pointer input should always be outermost so the hit area is not affected by internal padding,
clipping, or transforms. Semantics should be outermost so assistive technologies see the full
bounds.
