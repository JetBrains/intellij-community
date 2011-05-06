# argv: first argument is optional java architecture version ("64" or "32" bit)
on run argv
	# Determine path to "../../Contents/Info.plist"
	tell application "Finder"
		set binFolder to container of container of (path to me) as text
		set the plistPath to (POSIX path of binFolder) & ("/Contents/Info.plist")
	end tell
	
	# Fetch JVM system properties & VM options from Info.plist
	tell application "System Events"
		set the plistFile to property list file plistPath
		# Java section
		set javaOptionsSection to property list item "Java" of plistFile
		
		# Java | Properties sction
		set jvmProperties to property list items of property list item "Properties" of javaOptionsSection
		
		# Collect system properties as -D$name=$value
		set jvmSysPropertiesStr to ""
		repeat with jvmProperty in jvmProperties
			set propertyKey to name of jvmProperty
			set propertyValue to value of jvmProperty
			set jvmSysPropertiesStr to jvmSysPropertiesStr & " -D" & propertyKey & "=" & propertyValue
		end repeat
		
		# Collect vm options
		set vmOptionsStr to ""
		
		if (count argv) = 1 then
			set javaArchitecture to item 1 of argv
			
			# Common VM options
			set vmOptionsStr to value of property list item "VMOptions" of javaOptionsSection
			
			# Architecture - specific
			if javaArchitecture = "64" then
				set vmOptionsStr to vmOptionsStr & " " & value of property list item "VMOptions.x86_64" of javaOptionsSection
			else if javaArchitecture = "32" then
				set vmOptionsStr to vmOptionsStr & " " & value of property list item "VMOptions.i386" of javaOptionsSection
			end if
		end if
		
		return vmOptionsStr & " " & jvmSysPropertiesStr
	end tell
end run