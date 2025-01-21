package org.jetbrains.jewel.samples.ideplugin.releasessample

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@Language("GLSL") // Technically, SkSL
private const val FOIL_SHADER_CODE =
    """
const float SCALE = 1.8; // Effect scale (> 1 means smaller rainbow)
const float SATURATION = 0.9; // Color saturation (0.0 = grayscale, 1.0 = full color)
const float LIGHTNESS = 0.65; // Color lightness (0.0 = black, 1.0 = white)
 
uniform shader content; // Input texture (the application canvas)
uniform vec2 resolution;  // Size of the canvas
uniform vec2 offset;     // Additional offset of the effect
uniform float intensity; // 0.0 = no effect, 1.0 = full effect

// From https://www.ryanjuckett.com/photoshop-blend-modes-in-hlsl/
vec3 BlendMode_Screen(vec3 base, vec3 blend) {
	return base + blend - base * blend;
}

vec4 rainbowEffect(vec2 uv, vec2 coord, vec2 offset) {
    vec4 srcColor = content.eval(coord);
    if (srcColor.a == 0.0) return srcColor;
    
    float hue = uv.x / (1.75 + abs(offset.x)) + offset.x / 3.0;
    float lightness = LIGHTNESS + 0.25 * (0.5 + offset.y * (0.5 - uv.y));
    hue = fract(hue);

    float c = (1.0 - abs(2.0 * lightness - 1.0)) * SATURATION;
    float x = c * (1.0 - abs(mod(hue / (1.0 / 6.0), 2.0) - 1.0));
    float m = LIGHTNESS - c / 2.0;

    vec3 rainbowPrime;

    if (hue < 1.0 / 6.0) {
        rainbowPrime = vec3(c, x, 0.0);
    } else if (hue < 1.0 / 3.0) {
        rainbowPrime = vec3(x, c, 0.0);
    } else if (hue < 0.5) {
        rainbowPrime = vec3(0.0, c, x);
    } else if (hue < 2.0 / 3.0) {
        rainbowPrime = vec3(0.0, x, c);
    } else if (hue < 5.0 / 6.0) {
        rainbowPrime = vec3(x, 0.0, c);
    } else {
        rainbowPrime = vec3(c, 0.0, x);
    }

    vec3 rainbow = BlendMode_Screen(srcColor.rgb, rainbowPrime + m);
    return mix(srcColor, vec4(rainbow, srcColor.a), intensity);
}

vec4 chromaticAberration(vec2 coord, vec2 offset) {
    vec2 uv = coord / (resolution / SCALE);
    vec4 srcColor = rainbowEffect(uv, coord, offset);
    vec2 shift = offset * vec2(3.0, 5.0) / 1000.0;
    vec4 leftColor = rainbowEffect(uv - shift, coord, offset);
    vec4 rightColor = rainbowEffect(uv + shift, coord , offset);

    return vec4(rightColor.r, srcColor.g, leftColor.b, srcColor.a);
}

vec4 main(float2 fragCoord) {
    return chromaticAberration(fragCoord.xy, offset);
}
"""

private val runtimeEffect = RuntimeEffect.makeForShader(FOIL_SHADER_CODE)
private val shaderBuilder = RuntimeShaderBuilder(runtimeEffect)

internal fun Modifier.holoFoil(offset: Float, intensity: Float = 1f) = graphicsLayer {
    shaderBuilder.uniform("resolution", size.width, size.height)
    shaderBuilder.uniform("offset", 0f, offset)
    shaderBuilder.uniform("intensity", intensity * .65f)

    renderEffect =
        ImageFilter.makeRuntimeShader(
                runtimeShaderBuilder = shaderBuilder,
                shaderNames = arrayOf("content"),
                inputs = arrayOf(null),
            )
            .asComposeRenderEffect()

    rotationX = offset * 4f * intensity
    rotationY = offset * 10f * intensity
    rotationZ = offset * -3f * intensity
    scaleX = 1f - .1f * intensity
    scaleY = 1f - .1f * intensity
}
