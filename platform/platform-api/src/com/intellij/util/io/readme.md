# HttpRequests

## Get ByteArray, String or CharSequence

Convenience methods provided:

* `readBytes`
* `readString`
* `readChars`

```kotlin
fun example() {
  HttpRequests.request("https://example.com").readChars()
}
```

## Get Reader

If you have custom read logic, you can use `reader` or `getReader(ProgressIndicator)`:

```kotlin
fun example() {
  HttpRequests.request("https://example.com")
    .connect { it.reader.buffered().lines() /* use reader somehow and produce some result */ }
}
```

## Handle HTTP Errors

Call of some `read*` method throws `HttpRequests.HttpStatusException` exception.


```kotlin
fun example(): CharSequence? {
  try {
    return HttpRequests.request("https://example.com")
      .readChars()
  }
  catch (e: HttpRequests.HttpStatusException) {
    return null
  }
}
```

## Customize User-Agent

By default header `User-Agent` is not set, 
* use `productNameAsUserAgent()` to set to current product name and version (e.g. `IntelliJ IDEA/2018.2`).
* or use `userAgent(String)` to set to arbitrary value.