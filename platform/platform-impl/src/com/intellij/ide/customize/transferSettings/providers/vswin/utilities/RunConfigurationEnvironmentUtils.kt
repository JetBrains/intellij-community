package com.intellij.ide.customize.transferSettings.providers.vswin.utilities

/**
 * @author Ivan Migalev in Rider code
 */
object RunConfigurationEnvironmentUtils {

    private val invalidNameChars = Regex("[$(]")

    fun expandVariables(
        startSeparator: String,
        endSeparator: String,
        stringToExpand: String?,
        variableVisitor: (String) -> String?,
        removeNullVariables: Boolean,
        invalidNameChars: Regex?
    ): String? {
        if (stringToExpand == null) return null
        val resultBuilder = StringBuilder()
        var currentPos = 0
        while (currentPos < stringToExpand.length) {
            val startIndex = stringToExpand.indexOf(startSeparator, currentPos)
            if (startIndex < 0) break
            if (startIndex > currentPos) {
                resultBuilder.append(stringToExpand.substring(currentPos, startIndex))
                currentPos = startIndex
            }

            val endIndex = stringToExpand.indexOf(endSeparator, startIndex + startSeparator.length)
            if (endIndex < 0) break

            val name = stringToExpand.substring(startIndex + startSeparator.length, endIndex)
            if (name.isNotEmpty() && (invalidNameChars == null || !name.contains(invalidNameChars))) {
                val variableContent = variableVisitor(name)
                if (variableContent == null && removeNullVariables) {
                    currentPos = endIndex + endSeparator.length
                } else if (variableContent != null) {
                    resultBuilder.append(variableContent)
                    currentPos = endIndex + endSeparator.length
                    continue
                }
            }

            resultBuilder.append(stringToExpand.substring(currentPos, endIndex))
            currentPos = endIndex
        }

        resultBuilder.append(stringToExpand.substring(currentPos))
        return resultBuilder.toString()
    }

    /**
     * Extracts all the MSBuild $(variables) from the argument string.
     */
    fun extractMSBuildVariableNames(stringToExpand: String): Set<String> {
        val variables = mutableSetOf<String>()
        expandVariables("$(", ")", stringToExpand, { variables.add(it); "" }, true, invalidNameChars)
        return variables
    }

    /**
     * Processes the string containing percent-enclosed environment variable names ("%PATH%") and MSBuild-like substitutes
     * ("$(ProjectPath)"), expanding the variables.
     *
     * E.g. `"%PATH%; a b c"` -> `"/usr/bin:/bin; a b c"`; `"$(ProjectPath)"` -> `"/some/Project/Path"`.
     *
     * The environment map should be case-insensitive on Windows and case-sensitive on macOS and Linux, see
     * [com.intellij.util.EnvironmentUtil] for details. MSBuild environment should always be case-insensitive, and it's caller
     * responsibility to provide a case-insensitive map.
     *
     * See also `System.Environment.ExpandEnvironmentVariables`.
     *
     * This function will parse MSBuild variables first, and expand the percent-variables after that (corresponding to Visual Studio
     * behavior).
     */
    fun expandVariables(stringToExpand: String?, environment: Map<String, String?>, msBuild: Map<String, String?>): String? {
        val envExpansionResult = expandVariables("%", "%", stringToExpand, environment::get, false, null)
        return expandVariables("$(", ")", envExpansionResult, msBuild::get, true, invalidNameChars)
    }
}