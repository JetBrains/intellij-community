#!/usr/bin/env bash

#The MIT License (MIT)

#Copyright (c) 2008-2014 Andrey Tarantsov

#Permission is hereby granted, free of charge, to any person obtaining a copy
#of this software and associated documentation files (the "Software"), to deal
#in the Software without restriction, including without limitation the rights
#to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
#copies of the Software, and to permit persons to whom the Software is
#furnished to do so, subject to the following conditions:

#The above copyright notice and this permission notice shall be included in all
#copies or substantial portions of the Software.

#THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
#IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
#FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
#AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
#LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
#OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
#SOFTWARE.

# Create a read-only disk image of the contents of a folder

set -e;

function pure_version() {
	echo '1.0.0.6'
}

function version() {
	echo "create-dmg $(pure_version)"
}

function usage() {
	version
	echo "Creates a fancy DMG file."
	echo "Usage:  $(basename $0) [options] <output_name.dmg> <source_folder>"
	echo "All contents of source_folder will be copied into the disk image."
	echo "Options:"
	echo "  --volname name"
	echo "      set volume name (displayed in the Finder sidebar and window title)"
	echo "  --volicon icon.icns"
	echo "      set volume icon"
	echo "  --background pic.png"
	echo "      set folder background image (provide png, gif, jpg)"
	echo "  --window-pos x y"
	echo "      set position the folder window"
	echo "  --window-size width height"
	echo "      set size of the folder window"
	echo "  --text-size text_size"
	echo "      set window text size (10-16)"
	echo "  --icon-size icon_size"
	echo "      set window icons size (up to 128)"
	echo "  --icon file_name x y"
	echo "      set position of the file's icon"
	echo "  --hide-extension file_name"
	echo "      hide the extension of file"
	echo "  --custom-icon file_name custom_icon_or_sample_file x y"
	echo "      set position and custom icon"
	echo "  --app-drop-link x y"
	echo "      make a drop link to Applications, at location x,y"
	echo "  --ql-drop-link x y"
	echo "      make a drop link to user QuickLook install dir, at location x,y"
	echo "  --eula eula_file"
	echo "      attach a license file to the dmg"
	echo "  --no-internet-enable"
	echo "      disable automatic mount&copy"
	echo "  --format"
	echo "      specify the final image format (default is UDZO)"
	echo "  --add-file target_name file|folder x y"
	echo "      add additional file or folder (can be used multiple times)"
	echo "  --disk-image-size x"
	echo "      set the disk image size manually to x MB"
	echo "  --hdiutil-verbose"
	echo "      execute hdiutil in verbose mode"  
	echo "  --hdiutil-quiet"
	echo "      execute hdiutil in quiet mode"
	echo "  --sandbox-safe"
	echo "      execute hdiutil with sandbox compatibility and do not bless"
	echo "  --rez rez_path"
	echo "      use custom path to Rez tool"
	echo "  --version         show tool version number"
	echo "  -h, --help        display this help"
	exit 0
}

WINX=10
WINY=60
WINW=500
WINH=350
ICON_SIZE=128
TEXT_SIZE=16
FORMAT="UDZO"
ADD_FILE_SOURCES=()
ADD_FILE_TARGETS=()
IMAGEKEY=""
HDIUTIL_VERBOSITY=""
SANDBOX_SAFE=0

while test "${1:0:1}" = "-"; do
	case $1 in
	--volname)
		VOLUME_NAME="$2"
		shift; shift;;
	--volicon)
		VOLUME_ICON_FILE="$2"
		shift; shift;;
	--background)
		BACKGROUND_FILE="$2"
		BACKGROUND_FILE_NAME="$(basename $BACKGROUND_FILE)"
		BACKGROUND_CLAUSE="set background picture of opts to file \".background:$BACKGROUND_FILE_NAME\""
		REPOSITION_HIDDEN_FILES_CLAUSE="set position of every item to {theBottomRightX + 100, 100}"
		shift; shift;;
	--icon-size)
		ICON_SIZE="$2"
		shift; shift;;
	--text-size)
		TEXT_SIZE="$2"
		shift; shift;;
	--window-pos)
		WINX=$2; WINY=$3
		shift; shift; shift;;
	--window-size)
		WINW=$2; WINH=$3
		shift; shift; shift;;
	--icon)
		POSITION_CLAUSE="${POSITION_CLAUSE}set position of item \"$2\" to {$3, $4}
		"
		shift; shift; shift; shift;;
	--hide-extension)
		HIDING_CLAUSE="${HIDING_CLAUSE}set the extension hidden of item \"$2\" to true
		"
		shift; shift;;
	--custom-icon)
		shift; shift; shift; shift; shift;;
	-h | --help)
		usage;;
	--version)
		version; exit 0;;
	--pure-version)
		pure_version; exit 0;;
	--ql-drop-link)
		QL_LINK=$2
		QL_CLAUSE="set position of item \"QuickLook\" to {$2, $3}
		"
		shift; shift; shift;;
	--app-drop-link)
		APPLICATION_LINK=$2
		APPLICATION_CLAUSE="set position of item \"Applications\" to {$2, $3}
		"
		shift; shift; shift;;
	--no-internet-enable)
		NOINTERNET=1
		shift;;
	--format)
		FORMAT="$2"
		shift; shift;;
	--add-file | --add-folder)
		ADD_FILE_TARGETS+=("$2")
		ADD_FILE_SOURCES+=("$3")
		POSITION_CLAUSE="${POSITION_CLAUSE}
		set position of item \"$2\" to {$4, $5}
		"
		shift; shift; shift; shift; shift;;
	--disk-image-size)
		DISK_IMAGE_SIZE="$2"
		shift; shift;;
	--hdiutil-verbose)
		HDIUTIL_VERBOSITY='-verbose'
		shift;;
	--hdiutil-quiet)
		HDIUTIL_VERBOSITY='-quiet'
		shift;; 
	--sandbox-safe)
		SANDBOX_SAFE=1
		shift;; 
	--rez)
		REZ_PATH=$2
		shift; shift;;
	-*)
		echo "Unknown option $1. Run with --help for help."
		exit 1;;
	esac
	case $FORMAT in
	UDZO)
		IMAGEKEY="-imagekey zlib-level=9";;
	UDBZ)
		IMAGEKEY="-imagekey bzip2-level=9";;
	esac
done

test -z "$2" && {
	echo "Not enough arguments. Invoke with --help for help."
	exit 1
}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DMG_PATH="$1"
DMG_DIRNAME="$(dirname "$DMG_PATH")"
DMG_DIR="$(cd "$DMG_DIRNAME" > /dev/null; pwd)"
DMG_NAME="$(basename "$DMG_PATH")"
DMG_TEMP_NAME="$DMG_DIR/rw.${DMG_NAME}"
SRC_FOLDER="$(cd "$2" > /dev/null; pwd)"

test -z "$VOLUME_NAME" && VOLUME_NAME="$(basename "$DMG_PATH" .dmg)"


AUX_PATH="$SCRIPT_DIR/support"


if [ -f "$SRC_FOLDER/.DS_Store" ]; then
	echo "Deleting any .DS_Store in source folder"
	rm "$SRC_FOLDER/.DS_Store"
fi

# Create the image
echo "Creating disk image..."
test -f "${DMG_TEMP_NAME}" && rm -f "${DMG_TEMP_NAME}"

# Using Megabytes since hdiutil fails with very large Byte numbers
function blocks_to_megabytes() {
	# Add 1 extra MB, since there's no decimal retention here
	MB_SIZE=$((($1 * 512 / 1000 / 1000) + 1))
	echo $MB_SIZE
}

function get_size() {
	# Get block size in disk
	bytes_size=`du -s "$1" | sed -e 's/	.*//g'`
	echo `blocks_to_megabytes $bytes_size`
}

# Create the DMG with the specified size or the hdiutil estimation
CUSTOM_SIZE=''
if ! test -z "$DISK_IMAGE_SIZE"; then
	CUSTOM_SIZE="-size ${DISK_IMAGE_SIZE}m"
fi

if [ $SANDBOX_SAFE -eq 0 ]; then
	hdiutil create ${HDIUTIL_VERBOSITY} -srcfolder "$SRC_FOLDER" -volname "${VOLUME_NAME}" -fs HFS+ -fsargs "-c c=64,a=16,e=16" -format UDRW ${CUSTOM_SIZE} "${DMG_TEMP_NAME}"
else	
	hdiutil makehybrid ${HDIUTIL_VERBOSITY} -default-volume-name "${VOLUME_NAME}" -hfs -o "${DMG_TEMP_NAME}" "$SRC_FOLDER"
	hdiutil convert -format UDRW -ov -o "${DMG_TEMP_NAME}" "${DMG_TEMP_NAME}"
	DISK_IMAGE_SIZE_CUSTOM=$DISK_IMAGE_SIZE
fi

# Get the created DMG actual size
DISK_IMAGE_SIZE=`get_size "${DMG_TEMP_NAME}"`

# Use the custom size if bigger
if [ $SANDBOX_SAFE -eq 1 ] && [ ! -z "$DISK_IMAGE_SIZE_CUSTOM" ] && [ $DISK_IMAGE_SIZE_CUSTOM -gt $DISK_IMAGE_SIZE ]; then
	DISK_IMAGE_SIZE=$DISK_IMAGE_SIZE_CUSTOM
fi

# Estimate the additional soruces size
if ! test -z "$ADD_FILE_SOURCES"; then
	for i in "${!ADD_FILE_SOURCES[@]}"
	do
		SOURCE_SIZE=`get_size "${ADD_FILE_SOURCES[$i]}"`
		DISK_IMAGE_SIZE=$(expr $DISK_IMAGE_SIZE + $SOURCE_SIZE)
	done
fi

# Add extra space for additional resources
DISK_IMAGE_SIZE=$(expr $DISK_IMAGE_SIZE + 20)

# Resize the image for the extra stuff
hdiutil resize ${HDIUTIL_VERBOSITY} -size ${DISK_IMAGE_SIZE}m "${DMG_TEMP_NAME}"

# mount it
echo "Mounting disk image..."
MOUNT_DIR="/Volumes/${VOLUME_NAME}"

# try unmount dmg if it was mounted previously (e.g. developer mounted dmg, installed app and forgot to unmount it)
echo "Unmounting disk image..."
DEV_NAME=$(hdiutil info | egrep --color=never '^/dev/' | sed 1q | awk '{print $1}')
test -d "${MOUNT_DIR}" && hdiutil detach "${DEV_NAME}"

echo "Mount directory: $MOUNT_DIR"
DEV_NAME=$(hdiutil attach -readwrite -noverify -noautoopen "${DMG_TEMP_NAME}" | egrep --color=never '^/dev/' | sed 1q | awk '{print $1}')
echo "Device name:     $DEV_NAME"

if ! test -z "$BACKGROUND_FILE"; then
	echo "Copying background file..."
	test -d "$MOUNT_DIR/.background" || mkdir "$MOUNT_DIR/.background"
	cp "$BACKGROUND_FILE" "$MOUNT_DIR/.background/$BACKGROUND_FILE_NAME"
fi

if ! test -z "$APPLICATION_LINK"; then
	echo "making link to Applications dir"
	echo $MOUNT_DIR
	ln -s /Applications "$MOUNT_DIR/Applications"
fi

if ! test -z "$QL_LINK"; then
	echo "making link to QuickLook install dir"
	echo $MOUNT_DIR
	ln -s "/Library/QuickLook" "$MOUNT_DIR/QuickLook"
fi

if ! test -z "$VOLUME_ICON_FILE"; then
	echo "Copying volume icon file '$VOLUME_ICON_FILE'..."
	cp "$VOLUME_ICON_FILE" "$MOUNT_DIR/.VolumeIcon.icns"
	SetFile -c icnC "$MOUNT_DIR/.VolumeIcon.icns"
fi

if ! test -z "$ADD_FILE_SOURCES"; then
	echo "Copying custom files..."
	for i in "${!ADD_FILE_SOURCES[@]}"
	do
		echo "${ADD_FILE_SOURCES[$i]}"
		cp -a "${ADD_FILE_SOURCES[$i]}" "$MOUNT_DIR/${ADD_FILE_TARGETS[$i]}"
	done
fi

# run applescript
APPLESCRIPT=$(mktemp -t createdmg.tmp.XXXXXXXXXX)

function applescript_source() {
	cat << 'EOS'
on run (volumeName)
	tell application "Finder"
		tell disk (volumeName as string)
			open

			set theXOrigin to WINX
			set theYOrigin to WINY
			set theWidth to WINW
			set theHeight to WINH

			set theBottomRightX to (theXOrigin + theWidth)
			set theBottomRightY to (theYOrigin + theHeight)
			set dsStore to "\"" & "/Volumes/" & volumeName & "/" & ".DS_STORE\""

			tell container window
				set current view to icon view
				set toolbar visible to false
				set statusbar visible to false
				set the bounds to {theXOrigin, theYOrigin, theBottomRightX, theBottomRightY}
				set statusbar visible to false
				REPOSITION_HIDDEN_FILES_CLAUSE
			end tell

			set opts to the icon view options of container window
			tell opts
				set icon size to ICON_SIZE
				set text size to TEXT_SIZE
				set arrangement to not arranged
			end tell
			BACKGROUND_CLAUSE

			-- Positioning
			POSITION_CLAUSE

			-- Hiding
			HIDING_CLAUSE

			-- Application and QL Link Clauses
			APPLICATION_CLAUSE
			QL_CLAUSE
			close
			open
			-- Force saving of the size
			delay 1

			tell container window
				set statusbar visible to false
				set the bounds to {theXOrigin, theYOrigin, theBottomRightX - 10, theBottomRightY - 10}
			end tell
		end tell

		delay 1

		tell disk (volumeName as string)
			tell container window
				set statusbar visible to false
				set the bounds to {theXOrigin, theYOrigin, theBottomRightX, theBottomRightY}
			end tell
		end tell

		--give the finder some time to write the .DS_Store file
		delay 3

		set waitTime to 0
		set ejectMe to false
		repeat while ejectMe is false
			delay 1
			set waitTime to waitTime + 1
			
			if (do shell script "[ -f " & dsStore & " ]; echo $?") = "0" then set ejectMe to true
		end repeat
		log "waited " & waitTime & " seconds for .DS_STORE to be created."
	end tell
end run

EOS
}


applescript_source | sed -e "s/WINX/$WINX/g" -e "s/WINY/$WINY/g" -e "s/WINW/$WINW/g" -e "s/WINH/$WINH/g" -e "s/BACKGROUND_CLAUSE/$BACKGROUND_CLAUSE/g" -e "s/REPOSITION_HIDDEN_FILES_CLAUSE/$REPOSITION_HIDDEN_FILES_CLAUSE/g" -e "s/ICON_SIZE/$ICON_SIZE/g" -e "s/TEXT_SIZE/$TEXT_SIZE/g" | perl -pe  "s/POSITION_CLAUSE/$POSITION_CLAUSE/g" | perl -pe "s/QL_CLAUSE/$QL_CLAUSE/g" | perl -pe "s/APPLICATION_CLAUSE/$APPLICATION_CLAUSE/g" | perl -pe "s/HIDING_CLAUSE/$HIDING_CLAUSE/" >"$APPLESCRIPT"
sleep 2 # pause to workaround occasional "Can’t get disk" (-1728) issues  
echo "Running Applescript: /usr/bin/osascript \"${APPLESCRIPT}\" \"${VOLUME_NAME}\""
(/usr/bin/osascript "${APPLESCRIPT}" "${VOLUME_NAME}" || if [[ "$?" -ne 0 ]]; then echo "Failed running AppleScript"; hdiutil detach "${DEV_NAME}"; exit 64; fi)
echo "Done running the applescript..."
sleep 4
rm "$APPLESCRIPT"

# make sure it's not world writeable
echo "Fixing permissions..."
chmod -Rf go-w "${MOUNT_DIR}" &> /dev/null || true
echo "Done fixing permissions."

# make the top window open itself on mount:
if [ $SANDBOX_SAFE -eq 0 ]; then
	echo "Blessing started"
	bless --folder "${MOUNT_DIR}" --openfolder "${MOUNT_DIR}"
	echo "Blessing finished"
else
	echo "Skipping blessing on sandbox"
fi

if ! test -z "$VOLUME_ICON_FILE"; then
	# tell the volume that it has a special file attribute
	SetFile -a C "$MOUNT_DIR"
fi

# unmount
echo "Unmounting disk image..."
hdiutil detach "${DEV_NAME}"

# compress image
echo "Compressing disk image..."
hdiutil convert ${HDIUTIL_VERBOSITY} "${DMG_TEMP_NAME}" -format ${FORMAT} ${IMAGEKEY} -o "${DMG_DIR}/${DMG_NAME}"
rm -f "${DMG_TEMP_NAME}"


if [ ! -z "${NOINTERNET}" -a "${NOINTERNET}" == 1 ]; then
	echo "not setting 'internet-enable' on the dmg"
else
	# check if hdiutil supports internet-enable
	# support was removed in macOS 10.15
	# https://github.com/andreyvit/create-dmg/issues/76
	if hdiutil internet-enable -help >/dev/null 2>/dev/null 
	then
		hdiutil internet-enable -yes "${DMG_DIR}/${DMG_NAME}"
	else
		echo "hdiutil does not support internet-enable. Note it was removed in macOS 10.15."
	fi
fi

echo "Disk image done"
exit 0
