package mockplugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@OptIn(ExperimentalCompilerApi::class)
class MockCommandLineProcessor : CommandLineProcessor {

    companion object {
        const val PLUGIN_ID = "tests.mock-plugin"
        val ENABLED_KEY = CompilerConfigurationKey<Boolean>("mockPluginEnabled")
        val DEBUG_KEY = CompilerConfigurationKey<Boolean>("mockPluginDebug")
    }

    override val pluginId: String = PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Whether the plugin is enabled",
            required = false,
        ),
        CliOption(
            optionName = "debug",
            valueDescription = "<true|false>",
            description = "Enable debug logging",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option.optionName) {
            "enabled" -> configuration.put(ENABLED_KEY, value.toBoolean())
            "debug" -> configuration.put(DEBUG_KEY, value.toBoolean())
        }
    }
}