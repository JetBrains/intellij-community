#parse("File Header.java")
#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")
@Internal
package ${PACKAGE_NAME};

import org.jetbrains.annotations.ApiStatus.Internal;
#end
